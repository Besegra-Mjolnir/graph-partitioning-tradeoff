import java.io.{FileWriter, BufferedWriter}

import org.apache.spark._
import org.apache.spark.graphx._
import org.apache.spark.graphx.lib._
import au.com.bytecode.opencsv.CSVWriter
import scala.collection.JavaConverters._

object GraphPartitioningTradeoff {
  def main(args: Array[String]) {
    val outputCSVPath: String = sys.env("OUTPUT_CSV_PATH")
    val outputCSVBufferedWriter = new BufferedWriter(new FileWriter(outputCSVPath))
    val outputCSVWriter = new CSVWriter(outputCSVBufferedWriter)

    val graphFilePath: String = sys.env("GRAPH_FILE_PATH")
    val numIterationsList = List(1, 5, 10)
    val partitionStrategies = List(None, Some(PartitionStrategy.RandomVertexCut), Some(PartitionStrategy.EdgePartition1D), Some(PartitionStrategy.EdgePartition2D))
    val algorithms = List("PageRank", "ShortestPaths")

    val schemaArray = Array("algorithm", "partitioning_strategy", "num_iterations", "loading_time", "partitioning_time", "computation_time", "total_time")
    var outputCSVRowList: List[Array[String]] = List()
    try {
      for (run <- (0 to 2).toList) {
        for (algorithm <- algorithms) {
          for (partitionStrategy <- partitionStrategies) {
            for (numIterations <- numIterationsList) {
              outputCSVRowList ::= runGraphAlgorithm(algorithm, partitionStrategy, graphFilePath, numIterations)
            }
          }
        }
      }
    } finally {
      outputCSVWriter.writeAll((List(schemaArray) ++ outputCSVRowList).asJava)
      outputCSVBufferedWriter.close()
    }
  }

  def runGraphAlgorithm(algorithm: String, partitionStrategy: Option[PartitionStrategy], graphFilePath: String, numIterations: Int): Array[String] = {
    println(s"Running Graph Algorithm $algorithm with Partitioning Strategy: ${partitionStrategy.toString}, for graph: $graphFilePath, with numIterations: $numIterations")
    val conf = new SparkConf().setAppName("Graph Partitioning Tradeoff")
    val sc = new SparkContext(conf)
    val initialTimestamp: Long = System.currentTimeMillis

    var graph = GraphLoader.edgeListFile(sc, graphFilePath)

    val graphLoadedTimestamp: Long = System.currentTimeMillis
    val graphLoadingTime: Long = graphLoadedTimestamp - initialTimestamp
    println(s"Graph loading time: $graphLoadingTime")

    if (partitionStrategy.isDefined) {
      graph = graph.partitionBy(partitionStrategy.get)
      graph.edges.foreachPartition(x => {})
    }
    val graphPartitioningDoneTimestamp: Long = System.currentTimeMillis
    val graphPartitioningTime: Long = graphPartitioningDoneTimestamp - graphLoadedTimestamp
    println(s"Graph partitioning time: $graphPartitioningTime")

    // Run graph algorithm
    if (algorithm.equals("PageRank")) {
      PageRank.run(graph, numIterations)
    } else if (algorithm.equals("ShortestPaths")) {
      ShortestPaths.run(graph, graph.vertices.takeSample(true, numIterations).map(v => v._1))
    } else {
      throw new IllegalArgumentException(s"Invalid algorithm is selected: $algorithm")
    }
    val graphComputationDoneTimestamp: Long = System.currentTimeMillis
    val graphComputationTime: Long = graphComputationDoneTimestamp - graphPartitioningDoneTimestamp
    println(s"Graph computation time: $graphComputationTime")

    val totalTime: Long = graphComputationDoneTimestamp - initialTimestamp
    println(s"Total time: $totalTime")
    sc.stop()
    Array(
      algorithm,
      partitionStrategy.getOrElse("None").toString,
      numIterations.toString,
      graphLoadingTime.toString,
      graphPartitioningTime.toString,
      graphComputationTime.toString,
      totalTime.toString)
  }
}