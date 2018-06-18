/*
 * Copyright 2018 herd-mdl contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
**/
var casper = require('casper').create({
	pageSettings: {
		webSecurityEnabled: false
	}
});

casper.on('page.resource.received', function(resource) {
	//casper.echo(resource.url + ' ' + resource.status + ' ' + resource.contentType + ' ' + resource.bodySize);
});

casper.on('resource.received', function(resource) {
	//casper.echo('Received:' + resource.url + ' ' + JSON.stringify(resource));
});

casper.on('resource.requested', function(resource) {
	//casper.echo('Request:' + resource.url);
});

casper.on('page.initialized', function(page){
	//casper.echo(page.content);
});

casper.start(casper.cli.get('url'), function(){
	this.waitForSelector('div[id=response]');
});

casper.then(function(){
	casper.echo('SUCCESS: ' + this.getHTML('div#response'));
});

casper.on('resource.error', function(msg, backtrace){
	casper.echo('ERROR: ' + JSON.stringify(msg));
	window.setTimeout(function(){casper.exit(-1);},100);
});

casper.on('load.error', function(url){
	casper.echo('ERROR: ' + url);;
	window.setTimeout(function(){casper.exit(-1);},100);
});

// Casper will output a bogus error message if we dont execute it by using a setTimeout
casper.run(function () {
	this.page.close();

	window.setTimeout(function (){
		casper.exit(0);
	}, 150);
});