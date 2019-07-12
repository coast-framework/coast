(ns coast
  (:require [coast.potemkin.namespaces :as namespaces]
            [coast.components]
            [coast.middleware]
            [coast.router]
            [coast.core]
            [coast.response]
            [coast.db]
            [coast.db.connection]
            [coast.validator]
            [coast.utils]
            [coast.env]
            [error.core]
            [hiccup2.core])
  (:refer-clojure :exclude [update]))


(namespaces/import-vars
 [hiccup2.core
  raw]

 [coast.components
  doctype
  css
  js
  form]

 [coast.middleware
  layout
  logger
  assets
  json
  body-parser
  sessions
  reload
  not-found
  server-error
  content-type?
  head
  cookies
  security-headers]

 [coast.router
  middleware
  routes
  prefix
  url-for
  action-for
  redirect-to
  app]

 [coast.core
  apps
  server]

 [coast.response
  html
  redirect
  flash
  render]

 [coast.db
  q
  pull
  fetch
  insert
  update
  delete
  execute!
  find-by
  transaction
  upsert
  any-rows?
  defq]

 [coast.db.connection
  connection]

 [error.core
  raise
  rescue
  try*]

 [coast.validator
  params
  columns]

 [coast.utils
  uuid
  xhr?]

 [coast.env
  env])
