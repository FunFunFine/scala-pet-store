package io.github.pauljamescleary.petstore.domain.orders

import derevo.derive
import tofu.data.derived.ContextEmbed
import tofu.higherKind.derived.representableK

@derive(representableK)
trait OrderRepositoryAlgebra[F[_]] {
  def create(order: Order): F[Order]

  def get(orderId: Long): F[Option[Order]]

  def delete(orderId: Long): F[Option[Order]]
}

object OrderRepositoryAlgebra extends ContextEmbed[OrderRepositoryAlgebra] {

}
