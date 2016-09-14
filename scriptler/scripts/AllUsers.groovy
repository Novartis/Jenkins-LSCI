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
  "name" : "AllUsers.groovy",
  "comment" : "Generates a choice list with all Jenkins users",
  "parameters" : [ ],
  "core": "1.596",
  "authors" : [
    { name : "Ioannis K. Moutsatsos" }
  ]
} END META**/
  def computer =hudson.model.User
  allUsers= computer.getAll()
  
    sortedUsers= allUsers.sort{ a, b -> a.toString().compareToIgnoreCase b.toString() }
    return sortedUsers