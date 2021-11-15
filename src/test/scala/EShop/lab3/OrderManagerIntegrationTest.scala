package EShop.lab3

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.{ActorRef, Scheduler}
import akka.util.Timeout
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class OrderManagerIntegrationTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  import OrderManager._

  override implicit val timeout: Timeout = 1.second

  implicit val scheduler: Scheduler = testKit.scheduler

  def sendMessage(
    orderManager: ActorRef[OrderManager.Command],
    message: ActorRef[Any] => OrderManager.Command,
    tmp: String = "dupa"
  ): Unit = {
    import akka.actor.typed.scaladsl.AskPattern.Askable
    println(tmp)
    orderManager.ask[Any](message).mapTo[OrderManager.Ack].futureValue shouldBe Done
  }

  it should "supervise whole order process" in {
    val orderManager = testKit.spawn(new OrderManager().start).ref

    sendMessage(orderManager, AddItem("rollerblades", _), "add")

    sendMessage(orderManager, Buy, "buy")

    sendMessage(orderManager, SelectDeliveryAndPaymentMethod("paypal", "inpost", _), "select")

    sendMessage(orderManager, ref => Pay(ref))
  }

}
