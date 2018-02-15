(ns coast.alpha.generators
  (:require [clojure.string :as string]
            [word.core :as word]
            [coast.alpha.db :as db]))

(def pattern #"_([\w-]+)")

(defn replacement [match m]
  (let [default (first match)
        k (-> match last keyword)]
    (str (get m k default))))

(defn fill [s m]
  (string/replace s pattern #(replacement % m)))

(defn long-str [& strings]
  (clojure.string/join "\n" strings))

(defn model [project table]
  (-> (long-str
        "(ns _project.models._table"
        "  (:require [coast.db :as db])"
        "  (:refer-clojure :exclude [update list]))"
        ""
        "(def columns [_columns])"
        ""
        "(defn list [request]"
        "  (db/query :_table/list))"
        ""
        "(defn find-by-id [request]"
        "  (->> (select-keys (:params request) [:id])"
        "       (db/query :_table/find-by-id)))"
        ""
        "(defn insert [request]"
        "  (->> (select-keys (:params request) columns)"
        "       (db/insert :_table)))"
        ""
        "(defn update [request]"
        "  (let [{:keys [params]} request"
        "        {:keys [id]} params]"
        "    (as-> (select-keys params columns) %"
        "          (db/update :_table % :_table/where {:id id}))))"
        ""
        "(defn delete [request]"
        "  (->> (select-keys (:params request) [:id])"
        "       (db/delete :_table :_table/where)))")
      (fill {:table   (word/kebab table)
             :project      (word/kebab project)})))

(defn controller [project table]
  (-> (long-str
        "(ns _project.controllers._table"
        "  (:require [coast.core :as coast]"
        "            [_ns.models._table :as _table]"
        "            [_ns.views._table :as views._table])"
        "  (:refer-clojure :exclude [update]))"
        ""
        "(defn index [request]"
        "  (-> request"
        "      _table/list"
        "      views._table/index))"
        ""
        "(defn show [request]"
        "  (-> request"
        "      _table/find-by-id"
        "      views._table/show))"
        ""
        "(defn fresh [request]"
        "  (views._table/fresh request))"
        ""
        "(defn create [request]"
        "  (let [[_ errors] (-> request"
        "                       _table/create"
        "                       coast/try+)]"
        "    (if (empty? errors)"
        "       (-> (coast/redirect \"/_table\")"
        "           (coast/flash \"_singular created successfully\"))"
        "       (fresh (assoc request :errors errors))))"
        ""
        "(defn edit [request]"
        "  (-> request"
        "      _table/find-by-id"
        "      views._table/edit))"
        ""
        "(defn update [request]"
        "  (let [[_ errors] (-> request"
        "                       _table/find-by-id"
        "                       _table/update"
        "                       coast/try+)]"
        ""
        "    (if (empty? errors)"
        "      (-> (coast/redirect \"/_table\")"
        "          (coast/flash \"_singular updated successfully\"))"
        "      (edit (assoc request :errors errors))))"
        ""
        "(defn delete [request]"
        "  (let [_ (_table/delete request)]"
        "    (-> (coast/redirect \"/_table\")"
        "        (coast/flash \"_singular deleted successfully\"))))")
      (fill {:table   (word/kebab table)
             :project (word/kebab project)
             :singular (word/singular table)})))

(defn view [project table]
  (let [columns (->> (db/get-cols table)
                     (map :column_name)
                     (map word/kebab))
        th-columns (->> (map #(str "[:th \"" % "\"]") columns)
                        (string/join "\n            "))
        td-columns (->> (map #(str "[:td " % "]") columns)
                        (string/join "\n      "))
        div-columns (->> (map #(str "[:div " % "]") columns)
                         (string/join "\n      "))]
    (-> (long-str
          "(ns _project.views._table"
          "  (:require [_project.components :as c]"
          "            [coast.core :as coast]))"
          ""
          "(defn table-row [m]"
          "  (let [{:keys [_columns]} m"
          "        edit (coast/url [\"/_table/:id/edit\" m])"
          "        delete (coast/url [\"/_table/:id\" m])"
          "        show (coast/url [\"/_table/:id\" m])]"
          "    [:tr"
          "      _td-columns"
          "      [:td"
          "        [:a {:href edit} \"Edit\"]]"
          "      [:td"
          "        [:a {:href delete} \"Delete\"]]"
          "      [:td"
          "        [:a {:href show} \"Show\"]]]))"
          ""
          "(defn index [request]"
          "  (let [{:keys [_table]} request]"
          "    [:div"
          "      [:table"
          "        [:thead"
          "          [:tr"
          "            _th-columns]]"
          "        [:tbody"
          "          (for [m _table]"
          "            (table-row m))]]"
          "      [:div"
          "        [:a {:href \"/_table/fresh\"} \"New _singular\"]]))"
          ""
          "(defn show [request]"
          "  (let [{:keys [_singular]} request"
          "        {:keys [_columns]} _singular"
          "        delete-href (coast/url [:delete \"_table/:id\" _singular])]"
          "    [:div"
          "      _div-columns"
          "      [:div"
          "        [:a {:href delete-href} \"Delete\"]]"
          "      [:div"
          "        [:a {:href \"/_table\"} \"Back\"]]]))"
          "")
        (fill {:project (word/kebab project)
               :table (word/kebab table)
               :singular (word/singular table)
               :columns (string/join " " columns)
               :td-columns td-columns
               :th-columns th-columns
               :div-columns div-columns}))))
