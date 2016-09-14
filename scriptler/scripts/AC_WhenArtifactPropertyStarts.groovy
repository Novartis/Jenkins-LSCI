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
  "name" : "AC_WhenArtifactPropertyStarts.groovy",
  "comment" : "Generates a choice list from archived properties that start with propKeyStarts. vBuildRef in JOB_NAME#BUILD_NUMBER format",
  "parameters" : [ 'propFileName','propKeyStarts','vBuildRef'],
  "core": "1.596",
  "authors" : [
    { name : "Ioannis K. Moutsatsos" }
  ]
} END META**/
import hudson.model.*
jenkinsURL=jenkins.model.Jenkins.instance.getRootUrl()
def runParam=vBuildRef.split('#')
  def jobName=runParam[0]
  def buildNum=runParam[1]

def propAddress="${jenkinsURL}job/$jobName/$buildNum/artifact/$propFileName"
//def propKey= propKey //'metadata'

def props= new Properties()
props.load(new URL(propAddress).openStream())
def choices=[]
props.findAll{it.key.startsWith(propKeyStarts)}.each{
      choices.add(it as String)
        }
return choices