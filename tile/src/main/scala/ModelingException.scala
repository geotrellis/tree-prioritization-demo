package org.opentreemap.modeling

/** Represents an exception with a friendly error message that is
  * safe to expose to the public.
  */
class ModelingException(message: String) extends Exception(message)

