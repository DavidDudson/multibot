# About

This bot is based entirely on github.com/OlegYch/multibot
Although the only part that remains is the interpreter itself.

The main difference is that this bot runs on Gitters FAYE and REST api's, 
whereas the original multibot uses IRC.


# Features

- Multiline input and output
- Understands gitter's syntax
  - Supports both single line and multiline code blocks
  - Supports syntax highlighted code blocks
- Listens for edits to existing messages
  - Will allow adding of `!` to existing messages, if your message is the last one
  - Will update its own output based on this.
- Listens for message deletion
  - Will delete its message if this is the case
- Will modify your message to add syntax highlighting
  - Only works if the bot OWNS the channel (Current gitter limitation)

# Commands

- Ping
  - will return Pong
  - A standard command to make sure the bot is currently in the channel
- debug on/off
  - Will enable debugging
  - This currently only shows internal execution time   