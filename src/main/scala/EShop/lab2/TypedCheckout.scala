package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.language.postfixOps
import scala.concurrent.duration._
import EShop.lab3.{OrderManager, Payment}

object TypedCheckout {
  sealed trait Command
  case object StartCheckout                                                              extends Command
  case class SelectDeliveryMethod(method: String)                                        extends Command
  case object CancelCheckout                                                             extends Command
  case object ExpireCheckout                                                             extends Command
  case class SelectPayment(payment: String, orderManager: ActorRef[TypedCheckout.Event]) extends Command
  case object ExpirePayment                                                              extends Command
  case object ConfirmPaymentReceived                                                     extends Command

  sealed trait Event
  case object CheckOutClosed                                    extends Event
  case class PaymentStarted(payment: ActorRef[Payment.Command]) extends Event
  case object CheckoutStarted                                   extends Event
  case object CheckoutClosed                                    extends Event
  case object CheckoutCancelled                                 extends Event
  case class DeliveryMethodSelected(method: String)             extends Event

  sealed abstract class State(val timerOpt: Option[Cancellable])
  case object WaitingForStart                           extends State(None)
  case class SelectingDelivery(timer: Cancellable)      extends State(Some(timer))
  case class SelectingPaymentMethod(timer: Cancellable) extends State(Some(timer))
  case object Closed                                    extends State(None)
  case object Cancelled                                 extends State(None)
  case class ProcessingPayment(timer: Cancellable)      extends State(Some(timer))

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  def checkoutTimer(context: ActorContext[Command]): Cancellable =
    context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout)

  def paymentTimer(context: ActorContext[Command]): Cancellable =
    context.scheduleOnce(paymentTimerDuration, context.self, ExpirePayment)
}

class TypedCheckout(
  cartActor: ActorRef[TypedCartActor.Command],
  cartAdapter: ActorRef[TypedCartActor.Event] = null,
  checkoutAdapter: ActorRef[TypedCheckout.Event] = null,
  paymentAdapter: ActorRef[Payment.Event] = null
) {
  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  def start: Behavior[TypedCheckout.Command] = Behaviors.receive((context, msg) =>
    msg match {
      case StartCheckout => selectingDelivery(checkoutTimer(context))
      case _             => Behaviors.same
    }
  )

  def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive((context, msg) =>
    msg match {
      case SelectDeliveryMethod(method) =>
        timer.cancel()
        selectingPaymentMethod(paymentTimer(context))
      case CancelCheckout => cancelled
      case ExpireCheckout => cancelled
      case _              => Behaviors.same
    }
  )

  def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive((context, msg) =>
    msg match {
      case SelectPayment(method, sender) =>
        val payment = context.spawn(
          new Payment(method, context.self, cartAdapter, checkoutAdapter, paymentAdapter).start,
          "payment"
        )
        sender ! PaymentStarted(payment)
        processingPayment(timer)
      case CancelCheckout => cancelled
      case ExpirePayment  => cancelled
      case _              => Behaviors.same
    }
  )

  def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive((context, msg) =>
    msg match {
      case ConfirmPaymentReceived =>
        cartActor ! TypedCartActor.ConfirmCheckoutClosed
        closed
      case CancelCheckout =>
        cartActor ! TypedCartActor.ConfirmCheckoutCancelled
        cancelled
      case ExpirePayment =>
        cartActor ! TypedCartActor.ConfirmCheckoutCancelled
        cancelled
      case _ => Behaviors.same
    }
  )

  def cancelled: Behavior[TypedCheckout.Command] = Behaviors.receive((context, msg) =>
    msg match {
      case _ => Behaviors.same
    }
  )

  def closed: Behavior[TypedCheckout.Command] = Behaviors.receive((context, msg) =>
    msg match {
      case _ => Behaviors.same
    }
  )

}
