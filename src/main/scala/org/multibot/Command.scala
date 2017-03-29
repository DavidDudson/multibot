package org.multibot


object IntepretableMessage {
  def unapply(input: String): Option[String] =
    input match {
      case EmbeddedCommand(i) => Some(i)
      case PrefixedCommand(i) => Some(i)
      case _ => None
    }
}

/**
  * Examples
  * `! foo`
  * ```! foo```
  * ```scala\n! foo\n```
  */
object EmbeddedCommand {
  def unapply(input: String): Option[String] =
    Option(input)
      .map(Sanitizer.sanitizeInput)
      .filter(_.startsWith("! "))
      .map(_.drop(2))
}

/**
  * Examples
  * ! foo
  * ! `foo`
  * ! ```foo```
  * ! ```scala\nfoo\n```
  */
object PrefixedCommand {
  def unapply(input: String): Option[String] =
    Option(input)
      .filter(_.startsWith("! "))
      .map(_.drop(2))
      .map(Sanitizer.sanitizeInput)
}