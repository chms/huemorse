## huemorse-client

The huemorse-client is a collection of php script to be deployed on a webserver, which should have a way to connect to the huemorse-server.

Visit http://huemorse.chschmid.com for a demo.

huemorse-client consists of

* config.php
* morse.php
* log.php
* huemorse.db

### config.php

For basic configurations

### morse.php

Accepts plaintext messages via a GET parameter 'msg' which is converted to Morse code after all special characters have been removed. The Morse code message is then passed on to the huemorse-server. Every message is logged in an sqlite3 database.

### log.php

Interfaces the sqlite3 database 'huemorse.db' and return all messages that have been sent, or only the latest x messages when the GET parameter 'limit' is set to x.

### huemorse.db

The sqlite3 database file.