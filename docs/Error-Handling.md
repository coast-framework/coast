# Handling Errors

* [Introduction](#user-content-introduction)
* [Handling Errors](#user-content-handling-errors)
* [Custom Errors](#user-content-custom-errors)

Coast attempts to make clojure exceptions a little nicer by offering two functions: `raise` and `rescue`

In this guide, we learn how clojure exceptions are raised, how to write logic around them and finally creating your own custom exceptions.

## Introduction
Exceptions are great since they halt the program at a certain stage and make sure everything is correct before proceeding.

Exceptions, especially in clojure, are usually just treated as insane, indecipherable walls of text that tell devs that *something* went wrong, go dive in and find it.

By default, Coast handles all exceptions for you and displays them in a nice format during development. However, you are free to handle exceptions however you want.

## Handling Errors
Errors can be handled by catching all of them or specifying a name

### Gotta Catch 'Em All
Here's how `raise` works with one argument

```clojure
(raise {:message "This is an error with a message key"})
```

That raises a `clojure.lang.ExceptionInfo` exception with `ex-data`: `{:message "This is an error with a message key"}`

You can `rescue` from this instead of using `try` and `catch` like this:

```clojure
(let [[_ error] (rescue
                  (raise {:message "This is an error"}))])
```

The error variable in the above example now contains `{:message "This is an error"}`

So `rescue` is a macro, which wraps the body in `try` and `catch` and catches any `ExceptionInfo` that comes from `raise`

### Named Errors
You can rescue individual errors as well

```clojure
(rescue
  (raise {:message "Error!" :custom true})
  (raise {:message "Error!"})
  :custom)
```

In the above example, the first error will be caught, the second one will not.

## Custom Errors
`raise` can also change the "Error has occurred" message as well like this:

```clojure
(raise "This is a custom error title" {})
```
