/*
Copyright 2016 Novartis Institutes for BioMedical Research Inc.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

/*** BEGIN META {
  "name" : "AC_RefHelper_getSelectArtifactUrl",
  "comment" : "Returns a comma separated list of artifact URLs that contain the 'selector' string as input HTML",
  "parameters" : [ 'vBuildRef','vSelector'],
  "core": "1.593",
  "authors" : [
    { name : "Ioannis Moutsatsos" }
  ]
} END META**/
import hudson.model.*
def choices=[]
def project=vBuildRef.split('#')[0]
def buildNo=vBuildRef.split('#')[1] 
  def job = hudson.model.Hudson.instance.getItem(project) 
def build=job.getBuildByNumber(buildNo.toInteger())
  selector=vSelector
env=[:] 
env= build.getEnvironment(TaskListener.NULL);
buildURL=env['BUILD_URL']
artifact=[]
   artifact= build.getArtifacts()
   artifact.each{
     if((it as String).contains(selector)){
     choices.add("${buildURL}artifact/$it")
     }//end if contains
       }  
theValue=choices.join(',')

return '<input name="value" value="'+theValue+'" class="setting-input" type="text">'