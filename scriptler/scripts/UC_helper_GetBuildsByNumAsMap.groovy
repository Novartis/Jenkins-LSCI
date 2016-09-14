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
  "name" : "AC_helper_GetBuildsByNumAsMap",
  "comment" : "Returns a map of builds within numbered constraints. Use default Java API format for key",
  "parameters" : [ 'vSearchSpace','f','l'],
  "core": "1.593",
  "authors" : [
    { name : "Ioannis Moutsatsos" }
  ]
} END META**/

/*
j: Required; A comma delimited list of Job names
f      : Required; lowest range limit 
l      : Required; upper range limit or null for all
*/
import jenkins.model.*
def options= new HashMap()
def uc_key='' //uno-choice key: stored in parameter
def uc_value='' //uno-choice value: displayed to user
def buildSet=[]
  def buildSetHr=[:] //a map with human readable value
  options.'f'=f // first build number to include
  options.'l'=l //last build number to include

jobNames=vSearchSpace.split(',')
jobNames.each{ job->
  job=job.trim()
jenkins.model.Jenkins.instance.getItem(job).getBuilds().each{
  println it.number.toInteger()
  println it.result
  println "\n"
  if (options.l!=''){
     if (it.number.toInteger()>=options.f.toInteger() && it.number.toInteger()<options.l.toInteger() && it.result.toString()=='SUCCESS'){
    buildSet.add(it)
  } }else{
      if (it.number.toInteger()>=options.f.toInteger()&& it.result.toString()=='SUCCESS'){
    buildSet.add(it)
  }
      }
}
} //end each JobNames


    buildSet.each{
      uc_key="${it.project.name}#${it.number}" as String
      uc_value="${it.project.name} #${it.number} ${it.getDisplayName()}" as String
      
    buildSetHr.put(uc_key,uc_value)
    }
return buildSetHr
