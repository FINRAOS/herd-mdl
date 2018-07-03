## Public Open Source Project Website Template
This repo is used as a template codebase for public open source project website development.  You can start building content for the marketing page and build documentation using MD files.

### Geting the template site up and running locally
1. Clone the repo
2. Install MSL by running "npm install -g msl-server" (our awesome OS project!) - https://github.com/FINRAOS/msl
3. Go to the checkout directory
4. Run "msl"
5. Open up browser and goto http://localhost:8000

** If you are having trouble installing msl, you can host your static web content using another web server such as node server or nginx **

### Developing MD style documentation
We are using a tool called MkDocs to generate html files using MD files.  You create MD files and run MkDocs to create static html files.

#### Getting MkDocs up and running to start developing
1. Install MkDocs following instruction here - https://www.mkdocs.org/#installation
2. Go to "mkdocs" directory from the checkout directory
3. Run "mkdocs serve"
4. Open up browser and goto http://localhost:8000 (make sure you are not running msl at the same time)
5. Now make changes to mkdocs.yml and your MD files to see how it's going to look

#### Integrating developed MD files to the website
1. From "mkdocs" folder, run "mkdocs build --clean".  This generates "site" directory.
2. Copy content of "site" directory to the "docs" directory from the root of your checkout directory.
3. Run "msl" from the checkout directory
4. Open up browser and goto http://localhost:8000
5. Click on "GETTING STARTED" from the main page
6. You can now see how your MD style documentation looks inside of the website
