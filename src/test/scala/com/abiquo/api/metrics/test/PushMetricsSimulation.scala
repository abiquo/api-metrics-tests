package com.abiquo.api.metrics.test

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class PushMetricsSimulation extends Simulation
{
  // Simulation configuration
  val API_BASE_URL = scala.util.Properties.envOrElse("abiquo.gatling.api", "http://localhost:80/api")
  val API_USER = scala.util.Properties.envOrElse("abiquo.gatling.user", "admin")
  val API_PASS = scala.util.Properties.envOrElse("abiquo.gatling.pass", "xabiquo")

  val httpConf = http
    .baseURL(API_BASE_URL)
    .doNotTrackHeader("1")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .acceptEncodingHeader("gzip, deflate")
    .userAgentHeader("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0")
    .basicAuth(API_USER, API_PASS)

  // Actions
  val create_metric = 
    http("create_custom_metric")
      .post("${url}/metrics")
      .header(HttpHeaderNames.ContentType, "application/vnd.abiquo.custommetric+xml")
      .header(HttpHeaderNames.Accept, "application/vnd.abiquo.custommetric+xml")
      .body(StringBody("<custommetric><name>${metric}</name></custommetric>"))
      .check(status.is(201))

  val push_datapoints = 
    http("push_datapoints")
      .post("${url}/metrics/${metric}")
      .header(HttpHeaderNames.ContentType, "application/vnd.abiquo.datapoints+xml")
      .body(StringBody("${set}"))
      .check(status.is(204))
  
  // Feeders
  val NumberOfMetricsPerVm = 4
  val NumberOfVirtualMachines = 4
  val SizeOfDatapointsSet = 5

  val vms = jsonFile("vms.csv").circular
  val datapoints = Iterator.continually(Map("set" -> (random_dataset(SizeOfDatapointsSet))))
  val metrics = random_metrics(NumberOfMetricsPerVm).circular

  def random_dataset(size: Int) : String = {
    val random = new util.Random
    var timestamp = System.currentTimeMillis
    val builder = new StringBuilder()

    builder.append("<datapoints>")
    for (i <- 0 to size)
    {
      builder.append("<datapoint>")
      builder.append("<timestamp>" + timestamp + "</timestamp>")
      builder.append("<longValue>" + random.nextLong() + "</longValue>")
      builder.append("</datapoint>")
      timestamp = timestamp + 60000;
    }
    builder.append("</datapoints>").toString
  }

  def random_metrics(size: Int) : Array[Map[String, String]] = {
    var array = Array[Map[String, String]]()

    for (i <- 0 to size)
    {
      array = array :+ Map("metric" -> java.util.UUID.randomUUID.toString)
    }

    return array
  }

  // Scenarios
  val create_metrics = scenario("Create custom metrics").repeat(NumberOfVirtualMachines)
  {
    feed(vms).repeat(NumberOfMetricsPerVm) {
      feed(metrics)
      .exec(create_metric)
    }
  }
  
  val push_metrics = scenario("Push datapoints to vm").feed(vms).repeat(NumberOfMetricsPerVm) {
    feed(datapoints)
    .feed(metrics)
    .exec(push_datapoints)
  }
  
  setUp(
    create_metrics.inject(atOnceUsers(1)).protocols(httpConf)
    , push_metrics.inject(rampUsers(100).over(120)).protocols(httpConf)
  )
}
