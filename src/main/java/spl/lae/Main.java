package spl.lae;

import java.io.IOException;

import parser.ComputationNode;
import parser.InputParser;
import parser.OutputWriter;

public class Main {
  public static void main(String[] args) throws IOException {
    // making sure we got 3 arugments as required: num of threads, input path ,
    // output path
    if (args.length != 3) {
      System.err.println("given input is not in right format: required: num of threads, input path , output path");
      return;
    }
    int numOfThreads;
    String inputPath = args[1];
    String outputPath = args[2];

    try {
      // might throw an error if its not int
      numOfThreads = Integer.parseInt(args[0]);
      InputParser parser = new InputParser();
      // loading root to be given root from input path
      ComputationNode root = parser.parse(inputPath);
      // initializing LAE engine and starting calculation
      LinearAlgebraEngine lae = new LinearAlgebraEngine(numOfThreads);
      // keeping answer in res
      ComputationNode res = lae.run(root);
      // converting result root into a double[][]matrix
      double[][] resultMat = res.getMatrix();
      // writing the result in output
      OutputWriter.write(resultMat, outputPath);
    } catch (Exception e) {
      // logging errors on output
      OutputWriter.write(e.getMessage(), outputPath);
    }
  }
}