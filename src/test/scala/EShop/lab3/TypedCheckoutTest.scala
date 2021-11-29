package EShop.lab3

import EShop.lab2.{Cart, TypedCartActor, TypedCheckout}
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import akka.actor.testkit.typed.scaladsl.TestInbox
import akka.actor.typed.ActorRef
import akka.actor.testkit.typed.Effect

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

class TypedCheckoutTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  import TypedCheckout._

  override def afterAll(): Unit =
    testKit.shutdownTestKit()

  it should "Send close confirmation to cart" in {
    val inbox = testKit.createTestProbe[TypedCartActor.Command]()

    val inbox2 = testKit.createTestProbe[TypedCheckout.Event]()

    val checkout = testKit.spawn(new TypedCheckout(inbox.ref).start, "checkout")
    val om       = testKit.spawn(new OrderManager().start, "om")

    checkout ! TypedCheckout.StartCheckout
    checkout ! TypedCheckout.SelectDeliveryMethod("post")
    checkout ! TypedCheckout.SelectPayment("paypal", inbox2.ref)
    checkout ! TypedCheckout.ConfirmPaymentReceived
    inbox.expectMessage(TypedCartActor.ConfirmCheckoutClosed)

  }

  it should "start payment" in {
    val checkout = BehaviorTestKit(new TypedCheckout(testKit.spawn(new TypedCartActor().start).ref).start)
    val inbox    = TestInbox[OrderManager.Command]()

    val inbox2 = TestInbox[TypedCheckout.Event]()

    checkout.run(StartCheckout)
    checkout.run(SelectDeliveryMethod("post"))
    checkout.run(SelectPayment("paypal", inbox2.ref))
    checkout.expectEffectType[Effect.Scheduled[TypedCheckout.Command]]
    checkout.expectEffectType[Effect.Scheduled[TypedCheckout.Command]]
    checkout.expectEffectType[Effect.Spawned[Payment.Command]]
  }

}
