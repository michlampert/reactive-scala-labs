package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{Behaviors, ActorContext}
import akka.actor.typed.{ActorRef, Behavior}
import scala.language.postfixOps

import scala.concurrent.duration._
import EShop.lab3.OrderManager
import EShop.lab3.Payment

object TypedCheckout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                                                                  extends Command
  case class SelectDeliveryMethod(method: String)                                            extends Command
  case object CancelCheckout                                                                 extends Command
  case object ExpireCheckout                                                                 extends Command
  case class SelectPayment(payment: String, orderManager: ActorRef[OrderManager.Command]) extends Command
  case object ExpirePayment                                                                  extends Command
  case object ConfirmPaymentReceived                                                         extends Command

  sealed trait Event
  case object CheckoutClosed                           extends Event
  case class PaymentStarted(paymentRef: ActorRef[Any]) extends Event
}

class TypedCheckout(
  cartActor: ActorRef[TypedCartActor.Command]
) {
  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  private def checkoutTimer(context: ActorContext[Command]): Cancellable =
    context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout)

  private def paymentTimer(context: ActorContext[Command]): Cancellable =
    context.scheduleOnce(paymentTimerDuration, context.self, ExpirePayment)

  def start: Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, msg) => msg match {
      case StartCheckout => selectingDelivery(checkoutTimer(context))
      case _ => Behaviors.same
    }
  )

  def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, msg) => msg match {
      case SelectDeliveryMethod(method) => {
        timer.cancel()
        selectingPaymentMethod(paymentTimer(context))
      }
      case CancelCheckout => cancelled
      case ExpireCheckout => cancelled
      case _ => Behaviors.same
    }
  )

  def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, msg) => msg match {
      case SelectPayment(method, sender) => {
        val payment = context.spawn(new Payment(method, sender, context.self).start, "payment")
        sender ! OrderManager.ConfirmPaymentStarted(payment)
        processingPayment(timer)
      }
      case CancelCheckout => cancelled
      case ExpirePayment => cancelled
      case _ => Behaviors.same
    }
  )

  def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, msg) => msg match {
      case ConfirmPaymentReceived => {
        cartActor ! TypedCartActor.ConfirmCheckoutClosed
        closed
      }
      case CancelCheckout => {
        cartActor ! TypedCartActor.ConfirmCheckoutCancelled
        cancelled
      }
      case ExpirePayment => {
        cartActor ! TypedCartActor.ConfirmCheckoutCancelled
        cancelled
      }
      case _ => Behaviors.same
    }
  )

  def cancelled: Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, msg) => msg match {
      case _ => Behaviors.same
    }
  )

  def closed: Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, msg) => msg match {
      case _ => Behaviors.same
    }
  )

}
