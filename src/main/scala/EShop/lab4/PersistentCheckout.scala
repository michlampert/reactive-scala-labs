package EShop.lab4

import EShop.lab2.TypedCartActor
import EShop.lab3.{OrderManager, Payment}
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.concurrent.duration._
import EShop.lab2.TypedCheckout

class PersistentCheckout(
  cartRef: ActorRef[TypedCartActor.Command],
  orderManagerCartListener: ActorRef[TypedCartActor.Event] = null,
  orderManagerCheckoutListener: ActorRef[TypedCheckout.Event] = null,
  orderManagerPaymentListener: ActorRef[Payment.Event] = null
) {

  import EShop.lab2.TypedCheckout._

  val timerDuration: FiniteDuration = 1.seconds

  def schedule(context: ActorContext[Command]): Cancellable = ???

  def apply(cartActor: ActorRef[TypedCartActor.Command], persistenceId: PersistenceId): Behavior[Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior(
        persistenceId,
        WaitingForStart,
        commandHandler(context, cartActor),
        eventHandler(context)
      )
    }

  def commandHandler(
    context: ActorContext[Command],
    cartActor: ActorRef[TypedCartActor.Command]
  ): (State, Command) => Effect[Event, State] = (state, command) => {
    state match {
      case WaitingForStart =>
        command match {
          case StartCheckout =>
            Effect.persist(CheckoutStarted)

          case _ =>
            Effect.none
        }

      case SelectingDelivery(_) =>
        command match {
          case SelectDeliveryMethod(method) =>
            Effect.persist(DeliveryMethodSelected(method))

          case CancelCheckout =>
            Effect.persist(CheckoutCancelled)

          case ExpireCheckout =>
            Effect.persist(CheckoutCancelled)

          case _ =>
            Effect.none
        }

      case SelectingPaymentMethod(_) =>
        command match {
          case SelectPayment(payment, orderManager) =>
            val paymentRef = context.spawn(
              new Payment(
                payment,
                context.self,
                orderManagerCartListener,
                orderManagerCheckoutListener,
                orderManagerPaymentListener
              ).start,
              "payment"
            )
            orderManagerCheckoutListener ! PaymentStarted(paymentRef)

            Effect.persist(PaymentStarted(paymentRef))

          case CancelCheckout =>
            Effect.persist(CheckoutCancelled)

          case ExpireCheckout =>
            Effect.persist(CheckoutCancelled)
          case _ =>
            Effect.none
        }

      case ProcessingPayment(_) =>
        command match {
          case ConfirmPaymentReceived =>
            Effect.persist(CheckoutClosed)
          case CancelCheckout =>
            Effect.persist(CheckoutCancelled)
          case ExpireCheckout =>
            Effect.persist(CheckoutCancelled)
          case ExpirePayment =>
            Effect.persist(CheckoutCancelled)

          case _ =>
            Effect.none
        }

      case Cancelled =>
        command match {
          case StartCheckout =>
            Effect.persist(CheckoutStarted)

          case _ =>
            Effect.none
        }

      case Closed =>
        command match {
          case StartCheckout =>
            Effect.persist(CheckoutStarted)

          case _ =>
            Effect.none
        }
    }
  }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State = (state, event) => {
    state match {
      case WaitingForStart =>
        event match {
          case CheckoutStarted =>
            SelectingDelivery(TypedCheckout.checkoutTimer(context))
        }

      case SelectingDelivery(timer) =>
        event match {
          case DeliveryMethodSelected(_) => SelectingPaymentMethod(timer)
          case CheckoutCancelled =>
            timer.cancel()
            Cancelled
        }

      case SelectingPaymentMethod(timers) =>
        event match {
          case PaymentStarted(_) =>
            timers.cancel()
            ProcessingPayment(TypedCheckout.paymentTimer(context))
          case CheckoutCancelled =>
            timers.cancel()
            Cancelled
        }

      case ProcessingPayment(timers) =>
        event match {
          case CheckoutClosed =>
            timers.cancel()
            Closed
          case CheckoutCancelled =>
            timers.cancel()
            Cancelled
        }

      case Closed =>
        event match {
          case CheckoutStarted =>
            SelectingDelivery(TypedCheckout.checkoutTimer(context))
        }

      case Cancelled =>
        event match {
          case CheckoutStarted =>
            SelectingDelivery(TypedCheckout.checkoutTimer(context))
        }
    }
  }
}
