(ns coast.components
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [coast.env :refer [env]]
            [coast.assets :as assets]))

(defn csrf
  "Return a hidden input with the csrf token."
  ([attrs]
   [:input (assoc attrs
                  :type "hidden"
                  :name "__anti-forgery-token"
                  :value *anti-forgery-token*)])
  ([]
   (csrf {})))

(defn form
  "Return a form with the hidden input already added to the body"
  [params & body]
  [:form (dissoc params :_method)
   (csrf)
   (when (contains? #{:patch :put :delete} (:_method params))
     [:input {:type "hidden" :name "_method" :value (:_method params)}])
   body])

(defn css
  "Adds a link tag to a CSS bundle.

  Relative path (to CSS files in the public directory):

  ```clojure
  (coast/css \"bundle.css\")

  ; assuming the assets.edn looks something like this
  {\"bundle.css\" [\"style.css\"]}
  ```

  The code above outputs:

  ```html
  <link rel=\"stylesheet\" href=\"/style.css\" />
  ```
  "
  ([req bundle opts]
   (let [files (assets/bundle (env :coast-env) bundle)]
     (for [href files]
       [:link (merge {:href href :type "text/css" :rel "stylesheet"} opts)])))
  ([req bundle]
   (css nil bundle {}))
  ([bundle]
   (css nil bundle)))

(defn js
  "Adds a script tag to a JS bundle.

  ```clojure
  (coast/js \"bundle.js\")

  ; assuming the assets.edn looks something like this
  {\"bundle.js\" [\"app.js\" \"app2.js\"]}
  ```

  The code above outputs:

  ```html
  <script type=\"text/javascript\" src=\"/app.js\" />
  <script type=\"text/javascript\" src=\"/app2.js\" />
  ```
  "
  ([req bundle opts]
   (let [files (assets/bundle (env :coast-env) bundle)]
     (for [src files]
       [:script (merge {:src src :type "application/javascript"} opts)])))
  ([req bundle]
   (js nil bundle {}))
  ([bundle]
   (js nil bundle)))
