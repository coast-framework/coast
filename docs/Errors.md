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
(ns author.new
  (:require [coast :refer [url-for transact rescue redirect flash validate]]))

(defn view [req]
  ; pretend there's some form html here
  )

(defn encrypt-password [params]
  ; pretend there's an encryption function here
  params)

(defn create [{:keys [params]}]
  (let [[_ errors] (-> params
                       (validate [[:required [:author/nickname :author/email :author/password]]
                                  [:equal [:author/password :author/password-confirmation]
                                          "Password and confirmation password do not match"]
                                  [:min-length 12 :author/password]])
                       (select-keys [:author/nickname :author/email :author/password])
                       (encrypt-password)
                       (transact)
                       (rescue))]
    (if (nil? errors)
      (-> (redirect (url-for :home/index))
          (flash "Welcome to coast!"))
      (view (merge req errors)))))
```

Validation errors and database errors are now unified (again)! 🙌
