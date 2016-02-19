package elba

import java.util.UUID
import java.util.logging.LogManager

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._
import scala.language.postfixOps

import elba.samplewdl.SampleWdl
import elba.Helpers._

class SimpleTest extends Simulation {

  val logger = LogManager.getLogManager

  val rampUpTimeSecs = 30
  val noOfUsers      = 1
  val scatterWidth   = 2
  val repeatTimes    = 1
  val maxSubmitRetries = 20
  val minPollMs      = 1 minute
  val maxPollMs      = 3 minutes
  val minWaitMs      = 10 milliseconds
  val maxWaitMs      = 500 milliseconds

  //val baseURL      = "http://cromwell01.dsde-cromwell-dev.broadinstitute.org:8080"
  val baseURL      = "http://localhost:8000"
  val baseName     = "cromwell"

  val metadataRequestName  = baseName + " metadata"
  def metadataRequestURI(workflowId: String) = s"/api/workflows/v1/$workflowId/metadata"

  val statusCheckRequestName  = baseName + " status"
  def statusCheckRequestURI(workflowId: String) = s"/api/workflows/v1/$workflowId/status"

  val submitRequestURI   = s"/api/workflows/v1"

  val workflowIdSessionKey = "workflow_id"
  val workflowIdSessionKeyExpression = "${" + workflowIdSessionKey + "}"
  val currentStatusKey = "current_status"
  val currentStatusKeyExpression = "${" + currentStatusKey + "}"

  val httpConf = http
    .baseURL(baseURL)

  val http_headers = Map(
    "Accept-Encoding" -> "gzip,deflate",
    "Content-Type" -> "text/json;charset=UTF-8",
    "Keep-Alive" -> "115")

  // TODO: Make a "POST then find a value in the response using this regex" method
  def submit(wdl: String, inputs: String) = http("submit workflow")
      .post(submitRequestURI)
      .formParam("wdlSource", wdl.literally)
      .formParam("workflowInputs", inputs) // TODO: Optional input based on the tuple (paramName: String, paramValue: Option[String])
      .check(
        regex("""\{
                |  "id": "(([0-9a-f]|-)*)",
                |  "status": "Submitted"
                |\}""".stripMargin).saveAs(workflowIdSessionKey)
      ) // TODO: log what happens if this fails somehow...

  def checkMetadata(workflowId: String) =
    http("metadata")
      .get(metadataRequestURI(workflowId))
      .headers(http_headers)
      .check(status.is(200))

  // TODO: Make a "GET then find a value in the response using this regex" method
  def readStatus(workflowId: String) =
    http("read status")
      .get(statusCheckRequestURI(workflowId))
    .check(
      regex("""\{
              |  "id": ".*",
              |  "status": "([A-Za-z]*)"
              |\}""".stripMargin).saveAs(currentStatusKey)
    )

  def checkStatus(workflowId: String, requiredValue: String) =
    http("check status")
      .get(statusCheckRequestURI(workflowId))
      .check(
        regex(s"""\\{
                |  "id": ".*",
                |  "status": "$requiredValue"
                |\\}""".stripMargin).exists
      )

  val runId = UUID.randomUUID().toString.replace("-", "_")

  val submitAndVerify = scenario("submit and verify")
      .withUserNumber
      .repeat(times = repeatTimes) {
        // TODO: Method for "keep making request until a session attribute is set"
        asLongAs(session => session(workflowIdSessionKey).asOption[String].isEmpty && session("counter").as[Int] < maxSubmitRetries , "counter") {
          exec(submit(SampleWdl.WideScatter(runId), SampleWdl.WideScatterInputs(runId, scatterWidth)))
          .sideEffect( session =>
            // TODO: log, don't stderr this!
            System.err.println("User " + session(userNumberAttributeKey).as[String] + ": Workflow " + session(workflowIdSessionKey).asOption[String] + ". Attempt #" + session("counter").as[Int])
          )
          .doIf(session => session(workflowIdSessionKey).asOption[String].isEmpty) {
            // TODO: how to get sideEffect(f: Session => Unit) as the "first item in a chain"
            pause(minWaitMs, maxWaitMs)
          }
        }
        .exec(readStatus(workflowIdSessionKeyExpression))
        .asLongAs(session => session(currentStatusKey).as[String] == "Running") {
          exec(checkMetadata(workflowIdSessionKeyExpression))
          .exec(readStatus(workflowIdSessionKeyExpression))
          .pause(minPollMs, maxPollMs)
        }
        .sideEffect( session =>
          System.err.println("User " + session(userNumberAttributeKey).as[String] + ": Workflow is now: " + session(currentStatusKey).asOption[String])
        )
        .exec(checkStatus(workflowIdSessionKeyExpression, "Succeeded"))
        .exec(session => session.remove(workflowIdSessionKey))
        .exec(session => session.remove(currentStatusKey))
      }

  setUp(
    submitAndVerify.inject(rampUsers(noOfUsers) over (rampUpTimeSecs seconds))
  ).protocols(httpConf)
}