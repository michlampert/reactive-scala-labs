package EShop.lab3

import EShop.lab2.TypedCheckout
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import EShop.lab2.TypedCartActor

object Payment {

  sealed trait Command
  case object DoPayment extends Command
  
  sealed trait Event
  case object PaymentReceived extends Event
}

class Payment(
  method: String,
  orderManager: ActorRef[Payment.Event],
  checkout: ActorRef[TypedCheckout.Command],
  cartAdapter: ActorRef[TypedCartActor.Event] = null,
  checkoutAdapter: ActorRef[TypedCheckout.Event] = null,
  paymentAdapter: ActorRef[Payment.Event] = null
) {

  import Payment._

  def start: Behavior[Payment.Command] = Behaviors.receive {
    (context, msg) =>
      msg match {
        case DoPayment =>
          paymentAdapter ! PaymentReceived
          checkout ! TypedCheckout.ConfirmPaymentReceived
          Behaviors.stopped
    }
  }

}
