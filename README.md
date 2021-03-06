### Production info
[![Build Status](https://travis-ci.org/remcowesterhoud/backend.svg?branch=master)](https://travis-ci.org/remcowesterhoud/backend)
[![Coverage Status](https://coveralls.io/repos/github/remcowesterhoud/backend/badge.svg?branch=master)](https://coveralls.io/github/remcowesterhoud/backend?branch=master)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/f8c5b0c71cf142139b88aeafb09450c2)](https://www.codacy.com/app/remcowesterhoud/backend?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=remcowesterhoud/backend&amp;utm_campaign=Badge_Grade)

### Develop info
[![Build Status](https://travis-ci.org/remcowesterhoud/backend.svg?branch=develop)](https://travis-ci.org/remcowesterhoud/backend)
[![Coverage Status](https://coveralls.io/repos/github/remcowesterhoud/backend/badge.svg?branch=develop)](https://coveralls.io/github/remcowesterhoud/backend?branch=develop)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/f8c5b0c71cf142139b88aeafb09450c2?branch=develop)](https://www.codacy.com/app/remcowesterhoud/backend?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=remcowesterhoud/backend&amp;utm_campaign=Badge_Grade)

# League API
This is the backend for the League API project.

## Requirements
1. SBT
2. CouchDB
    * Make sure it runs on localhost:5984
    * Go to [Futon](http://localhost:5984/_utils/) and create the following databases: `matches-db` and `summoner-db`
3. Riot API development key (get yours at: [developer.riotgames.com](https://developer.riotgames.com/))
    * _(Optional)_ Put it as an environment variable named `RIOT_API_KEY`

## Usage
1. Set the `RIOT_API_KEY` variable (if you did not set it as environment variable):
   ``set RIOT_API_KEY=<your-api-key>``
2. Run it with `sbt run`
