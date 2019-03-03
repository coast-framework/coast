# Password Hashing

* [Buddy](#user-content-buddy)
* [Basic Example](#user-content-basic-example)

Coast does not ship with a generic encryption mechanism.

It does encrypt the session cookie, but that's internal to ring middleware.

## Buddy

[Buddy](https://github.com/funcool/buddy) is a mature hashing library composed of several different, smaller libraries:

- `buddy-core`
- `buddy-hashers`
- `buddy-sign`
- `buddy-auth`

Typically you will only need the `buddy-hashers` library for password hashing.

Here's how to set up buddy for use with a Coast application

Install the `buddy-hashers` dependency in your `deps.edn` file

```clojure
; deps.edn

{; other keys not shown
 :deps
 {org.clojure/clojure {:mvn/version "1.9.0"}
  coast-framework/coast.theta {:mvn/version "1.0.0"}
  org.xerial/sqlite-jdbc {:mvn/version "3.25.2"}
  buddy/buddy-hashers {:mvn/version "1.3.0"}}}
```

## Basic Example

You can see the [full documentation of buddy-hashers here](https://funcool.github.io/buddy-hashers/latest/), this short guide summarizes basic usage:

```clojure
(ns some-ns
  (:require [buddy.hashers :as hashers]))

(hashers/derive "secretpassword")
;; => "bcrypt+sha512$4i9sd34m..."

(hashers/check "secretpassword" "bcrypt+sha512$4i9sd34m...")
;; => true
```

Buddy uses the bcrypt + sha512 algorithm by default, although there are other algorithms available.
