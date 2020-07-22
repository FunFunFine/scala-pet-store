package io.github.pauljamescleary.petstore.domain
package orders

import cats.Functor
import cats.data.EitherT
import tofu.data.derived.ContextEmbed
import tofu.syntax.monadic._

class OrderService[F[_]: Functor](orderRepo: OrderRepositoryAlgebra[F]) {
  def placeOrder(order: Order): F[Order] =
    orderRepo.create(order)

  def get(id: Long): EitherT[F, OrderNotFoundError.type, Order] =
    EitherT.fromOptionF(orderRepo.get(id), OrderNotFoundError)

  def delete(id: Long): F[Unit] =
    orderRepo.delete(id).as(())
}

object OrderService extends ContextEmbed[OrderService] {
  def make[F[_]: Functor](implicit orderRepo: OrderRepositoryAlgebra[F]): OrderService[F] =
    new OrderService(orderRepo)
}
