# Welcome to Coast

__The full stack web framework for indie hackers__

## What is this?

Coast is a full stack web framework written in Clojure for small teams or solo developers. It uses a relational database and renders html on the sever without javascript which allows you to ship your web applications faster.

## Getting Started

Create a new coast website from your terminal

```bash
brew install clojure
curl -o /usr/local/bin/coast https://raw.githubusercontent.com/coast-framework/coast/master/coast
chmod a+x /usr/local/bin/coast
coast new myapp
cd myapp
make server
```

You should be greeted with the text "You're coasting on clojure!"
when you visit `http://localhost:1337`
