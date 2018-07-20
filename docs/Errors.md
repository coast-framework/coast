# Errors

Coast is cool and easy breezy, so `try` and `catch`? Get outta here, `raise` and `rescue` is where it's at.

Here's an example how to use raise/rescue (which was shamelessly stolen from ruby ❤️)

```clojure
(ns your-proj
  (:require [coast.error :refer [raise]]))

(raise {:message "Oh no! 😱"}) ; => clojure.lang.ExceptionInfo "An error has occurred" {:ex-data {:message "Oh no! 😱"}}

; or

(raise "Noooooooooo" {:some "data"}) ; => clojure.lang.ExceptionInfo "Noooooooooo" {:ex-data {:some "data"}}
```

And here is how they work together:

```clojure
(let [[_ m]
 (rescue (raise {:message "Oh no! 😱"}))])

; (= m {:message "Oh no! 😱"})
```

Coast uses this internally so something like this is now possible

```clojure
(ns author
  (:require [coast.db :as db]
            [coast.error :refer [rescue]]
            [coast.responses :as res]
            [coast.validation :as v]
            [routes :refer [url-for]))

(defn _new [req]
  ; pretend there's some form html here
  )

(defn encrypt-password [params]
  ; pretend there's an encryption function here
  params)

(defn create [req]
  (let [[_ errors] (-> (:params req)
                       (v/validate [[:required [::nickname ::email ::password]]
                                    [:equal [::password ::password-confirmation]
                                            "Password and confirmation password do not match"]
                                    [:min-length 12 ::password]])
                       (select-keys [::nickname ::email ::password])
                       (encrypt-password)
                       (db/transact)
                       (rescue))]
    (if (nil? errors)
      (-> (res/redirect (url-for :home/index))
          (res/flash "Welcome to coast!"))
      (_new (assoc req :errors errors)))))
```

Validation errors and database errors are now unified (again)! 🙌
