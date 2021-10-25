package EShop.lab3

import EShop.lab2.{TypedCartActor, TypedCheckout}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object OrderManager {

  sealed trait Command
  case class AddItem(id: String, sender: ActorRef[Ack])                                               extends Command
  case class RemoveItem(id: String, sender: ActorRef[Ack])                                            extends Command
  case class SelectDeliveryAndPaymentMethod(delivery: String, payment: String, sender: ActorRef[Ack]) extends Command
  case class Buy(sender: ActorRef[Ack])                                                               extends Command
  case class Pay(sender: ActorRef[Ack])                                                               extends Command
  case class ConfirmCheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command])                     extends Command
  case class ConfirmPaymentStarted(paymentRef: ActorRef[Payment.Command])                             extends Command
  case object ConfirmPaymentReceived                                                                  extends Command

  sealed trait Ack
  case object Done extends Ack //trivial ACK
}

class OrderManager {

  import OrderManager._

  def start: Behavior[OrderManager.Command] = Behaviors.setup {
    context => open(context.spawn(new TypedCartActor().start, "cart"))
  }

  def uninitialized: Behavior[OrderManager.Command] = Behaviors.receive(
    (context, msg) => Behaviors.same
  )

  def open(cartActor: ActorRef[TypedCartActor.Command]): Behavior[OrderManager.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case AddItem(item, sender) =>
          cartActor ! TypedCartActor.AddItem(item)
          sender ! Done
          Behaviors.same

        case RemoveItem(item, sender) =>
          cartActor ! TypedCartActor.RemoveItem(item)
          sender ! Done
          Behaviors.same

        case Buy(sender) =>
          cartActor ! TypedCartActor.StartCheckout(context.self)
          inCheckout(cartActor, sender)
        case _ => Behaviors.same
    }
  )

  def inCheckout(
    cartActorRef: ActorRef[TypedCartActor.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[OrderManager.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case ConfirmCheckoutStarted(checkoutRef) =>
          senderRef ! Done
          inCheckout(checkoutRef)
        case _ => Behaviors.same
    }
  )

  def inCheckout(checkoutActorRef: ActorRef[TypedCheckout.Command]): Behavior[OrderManager.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case SelectDeliveryAndPaymentMethod(delivery, payment, sender) =>
          checkoutActorRef ! TypedCheckout.SelectDeliveryMethod(delivery)
          checkoutActorRef ! TypedCheckout.SelectPayment(payment, context.self)
          inPayment(sender)
        case _ => Behaviors.same
    }
  )

  def inPayment(senderRef: ActorRef[Ack]): Behavior[OrderManager.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case ConfirmPaymentStarted(payment) =>
          senderRef ! Done
          inPayment(payment, senderRef)

        case ConfirmPaymentReceived =>
          senderRef ! Done
          finished
        case _ => Behaviors.same
    }
  )

  def inPayment(
    paymentActorRef: ActorRef[Payment.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[OrderManager.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case Pay(sender) =>
          paymentActorRef ! Payment.DoPayment
          inPayment(sender)
        case _ => Behaviors.same
    }
  )

  def finished: Behavior[OrderManager.Command] = Behaviors.stopped
}
