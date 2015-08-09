package com.memsql.superapp

import akka.pattern.ask
import akka.actor.ActorRef
import com.memsql.spark.context.{MemSQLSQLContext, MemSQLSparkContext}
import com.memsql.spark.etl.api._
import com.memsql.spark.etl.api.configs._
import com.memsql.superapp.api.{ApiActor, PipelineState, Pipeline}
import ApiActor._
import com.memsql.superapp.util.{BaseException, JarLoader}
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.{Time, StreamingContext}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
import spray.json._

case class PipelineConfigException(message: String) extends BaseException(message: String)

object PipelineMonitor {
  def of(api: ActorRef,
         pipeline: Pipeline,
         sparkContext: MemSQLSparkContext,
         sqlContext: MemSQLSQLContext,
         streamingContext: StreamingContext): Option[PipelineMonitor] = {
    try {
      var loadJar = false

      // XXX what
      val pipelineInstance = new ETLPipeline[Any] {
        var extractConfig: PhaseConfig = null
        var transformConfig: PhaseConfig = null
        var loadConfig: PhaseConfig = null
        try {
          extractConfig = ExtractPhase.readConfig(pipeline.config.extract.kind, pipeline.config.extract.config)
          transformConfig = TransformPhase.readConfig(pipeline.config.transform.kind, pipeline.config.transform.config)
          loadConfig = LoadPhase.readConfig(pipeline.config.load.kind, pipeline.config.load.config)
        } catch {
          case e: DeserializationException => throw new PipelineConfigException(s"config does not validate: $e")
        }

        override val extractor: Extractor[Any] = pipeline.config.extract.kind match {
          case ExtractPhaseKind.Kafka => new KafkaExtractor().asInstanceOf[Extractor[Any]]
          case ExtractPhaseKind.User => {
            loadJar = true
            val className = extractConfig.asInstanceOf[UserExtractConfig].class_name
            JarLoader.loadClass(pipeline.jar, className).asInstanceOf[Extractor[Any]]
          }
        }
        override val transformer: Transformer[Any] = pipeline.config.transform.kind match {
          //case TransformPhaseKind.Json => XXX we need a JSONTransformer class that takes no args
          case TransformPhaseKind.User => {
            loadJar = true
            val className = transformConfig.asInstanceOf[UserTransformConfig].class_name
            JarLoader.loadClass(pipeline.jar, className).asInstanceOf[Transformer[Any]]
          }
        }
        override val loader: Loader = pipeline.config.load.kind match {
          case LoadPhaseKind.MemSQL => new MemSQLLoader
          case LoadPhaseKind.User => {
            loadJar = true
            val className = loadConfig.asInstanceOf[UserLoadConfig].class_name
            JarLoader.loadClass(pipeline.jar, className).asInstanceOf[Loader]
          }
        }
      }

      if (loadJar) {
        //TODO does this pollute the classpath for the lifetime of the superapp?
        //TODO if an updated jar is appended to the classpath the superapp will always run the old version
        //distribute jar to all tasks run by this spark context
        sparkContext.addJar(pipeline.jar)
      }

      Some(PipelineMonitor(api, pipeline.pipeline_id, pipeline.batch_interval, pipeline.config, pipelineInstance, streamingContext, sqlContext))
    } catch {
      case e: Exception => {
        val errorMessage = Some(s"Failed to load class for pipeline ${pipeline.pipeline_id}: $e")
        Console.println(errorMessage)
        e.printStackTrace
        val future = (api ? PipelineUpdate(pipeline.pipeline_id, PipelineState.ERROR, error = errorMessage)).mapTo[Try[Boolean]]
        future.map {
          case Success(resp) => //exit
          case Failure(error) => Console.println(s"Failed to update pipeline ${pipeline.pipeline_id} state to ERROR: $error")
        }
        None
      }
    }
  }
}

case class PipelineMonitor(api: ActorRef,
                           pipeline_id: String,
                           batch_interval: Long,
                           pipelineConfig: PipelineConfig,
                           pipelineInstance: ETLPipeline[Any],
                           streamingContext: StreamingContext,
                           sqlContext: MemSQLSQLContext) {
  private var exception: Exception = null

  private val thread = new Thread(new Runnable {
    override def run(): Unit = {
      try {
        Console.println(s"Starting pipeline $pipeline_id")
        val future = (api ? PipelineUpdate(pipeline_id, PipelineState.RUNNING)).mapTo[Try[Boolean]]
        future.map {
          case Success(resp) => runPipeline
          case Failure(error) => Console.println(s"Failed to update pipeline $pipeline_id state to RUNNING: $error")
        }
      } catch {
        case e: InterruptedException => //exit
        case e: Exception => {
          exception = e
          Console.println(s"Unexpected exception: $e")
          val future = (api ? PipelineUpdate(pipeline_id, PipelineState.ERROR, error = Some(e.toString))).mapTo[Try[Boolean]]
          future.map {
            case Success(resp) => //exit
            case Failure(error) => Console.println(s"Failed to update pipeline $pipeline_id state to ERROR: $error")
          }
        }
      }
    }
  })

  def runPipeline(): Unit = {
    var extractConfig: PhaseConfig = null
    var transformConfig: PhaseConfig = null
    var loadConfig: PhaseConfig = null
    try {
      extractConfig = ExtractPhase.readConfig(pipelineConfig.extract.kind, pipelineConfig.extract.config)
      transformConfig = TransformPhase.readConfig(pipelineConfig.transform.kind, pipelineConfig.transform.config)
      loadConfig = LoadPhase.readConfig(pipelineConfig.load.kind, pipelineConfig.load.config)
    } catch {
      case e: DeserializationException => throw new PipelineConfigException(s"config does not validate: $e")
    }

    val inputDStream = pipelineInstance.extractor.extract(streamingContext, extractConfig)
    var time: Long = 0

    // manually compute the next RDD in the DStream so that we can sidestep issues with
    // adding inputs to the streaming context at runtime
    while (true) {
      time = System.currentTimeMillis

      inputDStream.compute(Time(time)) match {
        case Some(rdd) => {
          val df = pipelineInstance.transformer.transform(sqlContext, rdd.asInstanceOf[RDD[Any]], transformConfig)
          pipelineInstance.loader.load(df, loadConfig)

          Console.println(s"${inputDStream.count()} rows after extract")
          Console.println(s"${df.count()} rows after transform")
        }
        case None =>
      }

      Thread.sleep(Math.max(batch_interval - (System.currentTimeMillis - time), 0))
    }
  }

  def start(): Unit = {
    thread.start
  }

  def isAlive(): Boolean = {
    thread.isAlive
  }

  def stop() = {
    thread.interrupt
    thread.join
  }
}