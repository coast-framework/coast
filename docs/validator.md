# Validator

* [Validating Input](#user-content-validating-input)
* [Common Rules](#user-content-common-rules)

Coast makes it simple to validate input with the help of a validate function.

In this guide you learn how to validate data *manually*.

NOTE: Coast validation uses [verily](https://github.com/jkk/verily) under the hood. For full usage details, see the official verily [documentation](https://github.com/jkk/verily).

## Validating Input
Let's start with the example of validating input received via HTML form:

Make the routes to show the form and handle the submission:

```clojure
; src/routes.clj

(def routes
  (coast/site
    [:get "/posts/:post-id/edit" :post/edit]
    [:put "/posts/:post-id" :post/change]
    [:get "/posts/:post-id" :post/view])))
```

Make the handler functions to show the form

```clojure
; src/customer.clj

(defn edit [{:keys [params errors]}]
  [:div
    (when (some? errors)
      [:div
        [:div (:customer/email errors)]])
    (coast/form-for ::change {:customer/id (:customer/id params)}
      [:input {:type "text" :name "customer/email"}]
      [:button {:type "submit"} "Submit"])])
```

...handle the form submission and use the validator to validate the data:

```clojure
; src/customer.clj

(defn change [{:keys [params]}]
  (let [[_ errors] (-> (select-keys params [:customer/id :customer/email])
                       (coast/validate [[:email [:customer/email]]])
                       (coast/rescue))]
    (if (nil? errors)
      (coast/redirect-to ::view {:customer/id (:customer/id request)})
      (edit (merge request errors)))))
```

Let's break down the above code into small steps:

1. We [destructured](https://clojure.org/guides/destructuring) the request map into a params variable
2. We used the `validate` method to validate the params data against an `:email` rule
3. If validation fails, the form re-renders with any errors
4. If it succeeds, it redirects to the `::view` handler

## Common Rules
Below is the list of available, built in validator rules

- :required <keys> [msg] - must not be absent, blank, or nil
- :contains <keys> [msg] - must not be absent, but can be blank or nil
- :not-blank <keys> [msg] - may be absent but not blank or nil
- :exact <value> <keys> [msg] - must be a particular value
- :equal <keys> [msg] - all keys must be equal
- :email <keys> [msg] - must be a valid email
- :url <keys> [msg] - must be a valid URL
- :web-url <keys> [msg] - must be a valid website URL (http or https)
- :link-url <keys> [msg] - must be a valid link URL (can be relative, http: or https: may be omitted)
- :matches <regex> <keys> [msg] - must match a regular expression
- :min-length <len> <keys> [msg] - must be a certain length (for strings or collections)
- :max-length <len> <keys> [msg] - must not exceed a certain length (for strings or collections)
- :complete <keys> [msg] - must be a collection with no blank or nil values
- :min-val <min> <keys> [msg] - must be at least a certain value
- :max-val <max> <keys> [msg] - must be at most a certain value
- :within <min> <max> <keys> [msg] - must be within a certain range (inclusive)
- :positive <keys> [msg] - must be a positive number
- :negative <keys> [msg] - must be a negative number
- :after <date> <keys> [msg] - must be after a certain date
- :before <date> <keys> [msg] - must be before a certain date
- :in <coll> <keys> [msg] - must be contained within a collection
- :every-in <coll> <keys> [msg] - each value must be within a collection (for values that are themselves collections)
- :us-zip <keys> [msg] - must be a valid US zip code
- :luhn <keys> [msg] - must be pass the Luhn check (e.g., for credit card numbers)
- Datatype validations: :string, :boolean, :integer, :float, :decimal, :date (plus aliases)
- Datatype collection validations: :strings, :booleans, :integers, :floats, :decimals, :dates (plus aliases)

#### `(validate map rules)`
Validate required keys and one email key:

```clojure
(coast/validate {:customer/id 123
                 :customer/email "sean@example.com"} [[:required [:customer/id :customer/email]]
                                                      [:email [:customer/email]]])
```

NOTE: You can optionally pass custom error messages to return when your validation fails as the third value in each vector:

```clojure
(coast/validate {} [[:required [:customer/id] "can't be blank"]
                    [:email [:customer/email] "needs to be an actual email"]])
```

The exception that is raised from the above failed `(coast/validate)` validation looks like this:

```clojure
{:customer/email "Email needs to be an actual email"
 :customer/id "Id can't be blank"}
```
