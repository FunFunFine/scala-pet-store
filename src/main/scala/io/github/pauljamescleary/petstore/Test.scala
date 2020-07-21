package io.github.pauljamescleary.petstore

import tofu.env.Env
import tofu.{HasContext, WithLocal}
import tofu.optics.Contains
import tofu.optics.macros.ClassyOptics

@ClassyOptics
final case class Envi[F[_]](foo: String)

object Envi {
  implicit def subContext[F[_]: *[_] WithLocal Envi[F], C](implicit e: Envi[F] Contains C): F WithLocal C =
    WithLocal[F, Envi[F]].subcontext(e)
}

object IOTest {
  type AP[+A] = Env[Envi[AP], A]
  val x = implicitly[HasContext[AP, Envi[AP]]]
  val y = implicitly[HasContext[AP, String]]
}
