package EShop.lab4

import EShop.lab2.TypedCartActor._
import EShop.lab2.{Cart, TypedCheckout}
import EShop.lab3.OrderManager
import akka.actor.Cancellable
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.concurrent.duration._
import akka.actor.typed.ActorRef
import EShop.lab2.TypedCartActor
import EShop.lab3.Payment

class PersistentCartActor(
  orderManagerCartListener: ActorRef[TypedCartActor.Event],
  orderManagerCheckoutListener: ActorRef[TypedCheckout.Event],
  orderManagerPaymentListener: ActorRef[Payment.Event]
) {

  import EShop.lab2.TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5.seconds

  private def scheduleTimer(context: ActorContext[TypedCartActor.Command]): Cancellable =
    context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)

  var checkoutMapper: ActorRef[TypedCheckout.Event] = null

  def apply(persistenceId: PersistenceId): Behavior[Command] = Behaviors.setup { context =>
    checkoutMapper = context.messageAdapter(event =>
      event match {
        case TypedCheckout.CheckoutClosed    => ConfirmCheckoutClosed
        case TypedCheckout.CheckoutCancelled => ConfirmCheckoutCancelled
      }
    )
    EventSourcedBehavior[Command, Event, State](
      persistenceId,
      Empty(scheduleTimer(context)),
      commandHandler(context),
      eventHandler(context)
    )
  }

  def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] = (state, command) => {
    state match {
      case Empty(timers) =>
        command match {
          case AddItem(item) =>
            Effect.persist(ItemAdded(item))

          case _ =>
            Effect.none
        }

      case NonEmpty(cart, timers) =>
        command match {
          case AddItem(item) =>
            Effect.persist(ItemAdded(item))

          case RemoveItem(item) =>
            if (cart.contains(item)) {
              if (cart.size <= 1) {
                Effect.persist(CartEmptied)
              } else {
                Effect.persist(ItemRemoved(item))
              }
            } else
              Effect.none

          case StartCheckout(orderManagerRef) =>
            val checkoutRef = context.spawn(
              new TypedCheckout(
                context.self,
                orderManagerCartListener,
                orderManagerCheckoutListener,
                orderManagerPaymentListener
              ).start,
              "checkout"
            )
            checkoutRef ! TypedCheckout.StartCheckout

            orderManagerRef ! CheckoutStarted(checkoutRef)

            Effect.persist(CheckoutStarted(checkoutRef))

          case ExpireCart =>
            Effect.persist(CartExpired)
        }

      case InCheckout(_, timers) =>
        command match {
          case ConfirmCheckoutClosed =>
            Effect.persist(CheckoutClosed)

          case ConfirmCheckoutCancelled =>
            Effect.persist(CheckoutCancelled)

          case _ =>
            Effect.none
        }
    }
  }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State = (state, event) => {
    state match {
      case Empty(timers) =>
        event match {
          case ItemAdded(item) =>
            NonEmpty(Cart.empty.addItem(item), scheduleTimer(context))
        }

      case NonEmpty(cart, timer) =>
        event match {
          case ItemAdded(item) =>
            NonEmpty(cart.addItem(item), scheduleTimer(context))

          case ItemRemoved(item) =>
            NonEmpty(cart.removeItem(item), scheduleTimer(context))

          case CartEmptied =>
            timer.cancel()
            Empty(timer)

          case CheckoutStarted(checkout) =>
            checkout ! TypedCheckout.StartCheckout
            timer.cancel()
            InCheckout(cart, timer)

          case CartExpired =>
            Empty(timer)
        }

      case InCheckout(cart, timer) =>
        event match {
          case CheckoutClosed =>
            Empty(timer)

          case CheckoutCancelled =>
            NonEmpty(cart, scheduleTimer(context))
        }
    }
  }
}
