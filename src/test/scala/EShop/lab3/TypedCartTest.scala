package EShop.lab3

import EShop.lab2.{Cart, TypedCartActor}
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import akka.actor.testkit.typed.scaladsl.TestInbox
import akka.actor.testkit.typed.Effect
import EShop.lab2.TypedCheckout
import akka.actor.typed.ActorRef

class TypedCartTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  override def afterAll(): Unit =
    testKit.shutdownTestKit()

  import TypedCartActor._

  //use GetItems command which was added to make test easier
  it should "add item properly" in {
    val testKit = BehaviorTestKit(new TypedCartActor().start)
    val inbox   = TestInbox[Any]()

    testKit.run(TypedCartActor.AddItem("rollerblades"))
    testKit.run(TypedCartActor.GetItems(inbox.ref))
    inbox.expectMessage(Cart(Seq("rollerblades")))
  }

  it should "be empty after adding and removing the same item" in {
    val cart  = testKit.spawn(new TypedCartActor().start, "cart")
    val probe = testKit.createTestProbe[Any]()

    cart ! TypedCartActor.AddItem("rollerblades")
    cart ! TypedCartActor.RemoveItem("rollerblades")
    cart ! TypedCartActor.GetItems(probe.ref)
    probe.expectMessage(Cart.empty)
  }

  it should "start checkout" in {
    val testKit = BehaviorTestKit(new TypedCartActor().start)
    val inbox   = TestInbox[TypedCartActor.Event]()

    testKit.run(TypedCartActor.AddItem("rollerblades"))
    testKit.run(TypedCartActor.StartCheckout(inbox.ref))
    testKit.expectEffectType[Effect.Scheduled[TypedCartActor.Command]]
    testKit.expectEffectType[Effect.Spawned[TypedCheckout.Command]]
  }
}
