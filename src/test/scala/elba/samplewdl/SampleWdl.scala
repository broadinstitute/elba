package elba.samplewdl

object SampleWdl {
  val HelloWorld =
    """
      |task helloWorldTask {
      |    command { echo "Hello, World!" }
      |    output { String s = read_string(stdout()) }
      |}
      |
      |workflow helloWorldWf {
      |    call helloWorldTask
      |}
      |
    """.stripMargin

  val SlowClap =
    """
      |task slowClapTask {
      |    command {
      |      echo "Clap"
      |      sleep $[ 1 + $[ RANDOM % 10 ]]
      |      echo "Clap"
      |      sleep $[ 1 + $[ RANDOM % 10 ]]
      |      echo "Clap"
      |    }
      |    output { String s = read_string(stdout()) }
      |}
      |
      |workflow slowClap {
      |    call slowClapTask
      |}
      |
    """.stripMargin

  def WideScatter(runId: String) =
    """task A {
      |  command {
      |    sleep $(( ( RANDOM % 1200 )  + 1 ))
      |    echo "The creatures outside looked from pig to man, and from man to pig, and from pig to man again: but already it was impossible to say which was which." > B1
      |    echo "But it was all right, everything was all right, the struggle was finished. He had won the victory over himself. He loved Big Brother." > B2
      |  }
      |  output {
      |    Array[File] outs = [ "B1", "B2" ]
      |  }
      |  runtime {
      |    docker: "ubuntu:latest"
      |  }
      |}
      |
      |task prepareScatter {
      |    Int scatterWidth
      |    command {
      |        for i in `seq 1 ${scatterWidth}`
      |        do
      |            echo $i
      |        done
      |    }
      |    output {
      |        Array[Int] array = read_lines(stdout())
      |    }
      |   runtime {
      |    docker: "ubuntu:latest"
      |  }
      |}
      |
      |workflow wideScatter_""".stripMargin + runId + """ {
      |  call prepareScatter
      |
      |  scatter ( x in prepareScatter.array ) {
      |    call A as a1
      |  }
      |
      |  call A as a2
      |
      |  output {
      |    a1.*
      |    a2.*
      |  }
      |}""".stripMargin

  def WideScatterInputs(runId: String, scatterWidth: Int) =
    ("""
      |{
      |   "wideScatter_""" + runId + """.prepareScatter.scatterWidth": """" + scatterWidth + """"
      |}
    """).stripMargin

}
