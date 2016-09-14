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
  "name" : "RenameBuidlArtifact",
  "comment" : "Renames a build artifact to a new name",
  "parameters" : [ 'sourceName','targetName'],
  "core": "1.596",
  "authors" : [
    { name : "Ioannis K. Moutsatsos" }
  ]
} END META**/

def ant=new AntBuilder()
def sname=sourceName
def tname=targetName
sFile=new File(sname)
if (sFile.exists()){
ant.move(file:"$sname", toFile:"$tname")
}else{
println "Skipping rename: no file ${sFile.canonicalPath} exists!"
}