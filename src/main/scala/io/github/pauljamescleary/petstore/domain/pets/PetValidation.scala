package io.github.pauljamescleary.petstore.domain
package pets

import cats.Monad
import cats.data.EitherT
import tofu.syntax.monadic._
import cats.syntax.either._
import derevo.derive
import tofu.data.derived.ContextEmbed
import tofu.higherKind.derived.representableK

@derive(representableK)
trait PetValidation[F[_]] {
  /* Fails with a PetAlreadyExistsError */
  def doesNotExist(pet: Pet): EitherT[F, PetAlreadyExistsError, Unit]

  /* Fails with a PetNotFoundError if the pet id does not exist or if it is none */
  def exists(petId: Option[Long]): EitherT[F, PetNotFoundError.type, Unit]
}

object PetValidation extends ContextEmbed[PetValidation]{
  def make[F[_]: Monad](implicit repository: PetRepositoryAlgebra[F]): PetValidation[F] =
    new PetValidation[F] {
      def doesNotExist(pet: Pet): EitherT[F, PetAlreadyExistsError, Unit] = EitherT {
        repository.findByNameAndCategory(pet.name, pet.category).map { matches =>
          if (matches.forall(possibleMatch => possibleMatch.bio != pet.bio)) {
            Right(())
          } else {
            Left(PetAlreadyExistsError(pet))
          }
        }
      }

      def exists(petId: Option[Long]): EitherT[F, PetNotFoundError.type, Unit] =
        EitherT {
          petId match {
            case Some(id) =>
              // Ensure is a little tough to follow, it says "make sure this condition is true, otherwise throw the error specified
              // In this example, we make sure that the option returned has a value, otherwise the pet was not found
              repository.get(id).map {
                case Some(_) => Right(())
                case _ => Left(PetNotFoundError)
              }
            case _ =>
              Either.left[PetNotFoundError.type, Unit](PetNotFoundError).pure[F]
          }
        }
    }
}
