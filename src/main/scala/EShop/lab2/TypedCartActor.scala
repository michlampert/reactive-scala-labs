package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.language.postfixOps
import scala.concurrent.duration._
import EShop.lab3.OrderManager
import EShop.lab3.Payment

object TypedCartActor {

  sealed trait Command
  case class AddItem(item: Any)                                          extends Command
  case class RemoveItem(item: Any)                                       extends Command
  case object ExpireCart                                                 extends Command
  case class StartCheckout(orderManager: ActorRef[TypedCartActor.Event]) extends Command
  case object ConfirmCheckoutCancelled                                   extends Command
  case object ConfirmCheckoutClosed                                      extends Command
  case class GetItems(sender: ActorRef[Cart])                            extends Command

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command]) extends Event
  case class ItemAdded(item: Any)                                          extends Event
  case class ItemRemoved(item: Any)                                        extends Event
  case object CartEmptied                                                  extends Event
  case object CartExpired                                                  extends Event
  case object CheckoutClosed                                               extends Event
  case object CheckoutCancelled                                            extends Event

  sealed trait State
  case class Empty(timer: Cancellable)                  extends State
  case class NonEmpty(cart: Cart, timer: Cancellable)   extends State
  case class InCheckout(cart: Cart, timer: Cancellable) extends State
}

class TypedCartActor(
  cartAdapter: ActorRef[TypedCartActor.Event] = null,
  checkoutAdapter: ActorRef[TypedCheckout.Event] = null,
  paymentAdapter: ActorRef[Payment.Event] = null
) {

  import TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5 seconds

  private def scheduleTimer(context: ActorContext[TypedCartActor.Command]): Cancellable =
    context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)

  def start: Behavior[TypedCartActor.Command] = empty

  def empty: Behavior[TypedCartActor.Command] = Behaviors.receive((context, msg) =>
    msg match {
      case AddItem(item) => nonEmpty(Cart.empty.addItem(item), scheduleTimer(context))
      case GetItems(sender) =>
        sender ! Cart.empty
        Behaviors.same
      case _ => Behaviors.same
    }
  )

  def nonEmpty(cart: Cart, timer: Cancellable): Behavior[TypedCartActor.Command] = Behaviors.receive((context, msg) =>
    msg match {
      case AddItem(item) => nonEmpty(cart.addItem(item), timer)
      case RemoveItem(item) if cart.contains(item) && cart.size == 1 =>
        timer.cancel()
        empty
      case RemoveItem(item) if cart.contains(item) => nonEmpty(cart.removeItem(item), timer)
      case GetItems(sender) =>
        sender ! cart
        Behaviors.same
      case StartCheckout(sender) =>
        timer.cancel()
        val checkout =
          context.spawn(new TypedCheckout(context.self, cartAdapter, checkoutAdapter, paymentAdapter).start, "checkout")
        checkout ! TypedCheckout.StartCheckout
        sender ! CheckoutStarted(checkout)
        inCheckout(cart)
      case ExpireCart => empty
      case _          => Behaviors.same
    }
  )

  def inCheckout(cart: Cart): Behavior[TypedCartActor.Command] = Behaviors.receive((context, msg) =>
    msg match {
      case ConfirmCheckoutCancelled => nonEmpty(cart, scheduleTimer(context))
      case ConfirmCheckoutClosed =>
        empty
      case _ => Behaviors.same
    }
  )

}
