import {defineConfig} from '@playwright/test';
const port=process.env.LIBRARY_PORT || '59198';
const site=process.env.LIBRARY_SITE || 'examples/library-consumer/target/site';
export default defineConfig({
  testDir:'./browser-tests',timeout:30_000,
  use:{baseURL:'http://127.0.0.1:'+port,browserName:'chromium',channel:'chrome',headless:true},
  webServer:{command:`java -m jdk.httpserver -b 127.0.0.1 -p ${port} -d ${site}`,cwd:'../..',url:'http://127.0.0.1:'+port,reuseExistingServer:false},
  reporter:[['list']]
});
