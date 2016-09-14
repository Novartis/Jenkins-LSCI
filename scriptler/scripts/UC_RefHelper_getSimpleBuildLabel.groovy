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
  "name" : "AC_RefHelper_getSimpleBuildLabel",
  "comment" : "Create a simple build label with dynamic displayName and a static code suffix",
  "parameters" : [ 'vDataSet','vCode'],
  "core": "1.593",
  "authors" : [
    { name : "Ioannis Moutsatsos" }
  ]
} END META**/

/*
UC_RefHelper_getSimpleBuildLabel
Required parameters: vDataSet, vCode

Using the DATA_SET build displayName, we create a dynamic yet user editable build label
nameProper=nameHint+nameSeparator+parentName+nameSeparator+code
BUILD_LABEL=prefix+delimiter+nameProper+delimiter+suffix
*/

import hudson.model.*
import jenkins.model.*
import hudson.model.Fingerprint.RangeSet;
def prefix=''
def suffix=''
def delimiter=':'
def nameSeparator='.'
def nameHint=''
def code=vCode

/* Get the build displayName of the data set */
dataSet=vDataSet //dataSet assigned from PARENT analysis
analysisParam='' //an analysis parameter is used as needed

if (dataSet==null||dataSet=='')
 return '<input name="value" value="'+'SELECT A DATA_SET'+'" class="setting-input" type="text">'
if(code==null||code=='')
  {
    code=''
   nameSeparator=''
  }

def dataRun=dataSet.split('#')
def jobName=dataRun[0].trim()
def buildRange=dataRun[1]
def range = RangeSet.fromString(buildRange, true);
def bl=jenkins.model.Jenkins.instance.getItem(jobName).getBuilds(range) // return a FreeStyleBuild list
def b=bl[0]
def parentName=b.getDisplayName()

/*Construct Name Proper */
nameProper=parentName+nameSeparator+code

/*construct build display name
Full implementation is:
prefix+delimiter+nameProper+delimiter+suffix
 */
bDisplayName=nameProper
return '<input name="value" value="'+bDisplayName+'" class="setting-input" type="text">'
