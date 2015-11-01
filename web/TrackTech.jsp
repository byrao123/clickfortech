<%-- 
    Document   : TrackTech
    Created on : Nov 1, 2015, 2:05:53 PM
    Author     : nv
--%>

<%@page import="java.util.ArrayList"%>
<%@page import="java.util.List"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
  <%
       List list = new ArrayList();
       if(request.getAttribute("accountList") !=null){
           list = (List)request.getAttribute("accountList");
       }
       System.out.println("listlist "+list);
    %>

<html>
  <head>
    <title>Simple Map</title>
    <meta name="viewport" content="initial-scale=1.0">
    <meta charset="utf-8">
    <style>
      html, body {
        height: 100%;
        margin: 0;
        padding: 0;
      }
      #map {
        height: 70%;
      }
    </style>
  </head>
  <body>
    <div id="map"></div>
    <div>
        <table align="center">
            <tr>
                <td><input type="button" value="FIOS"></td>
                <td><input type="button" value="HSI"></td> 
                <td><input type="button" id="advancedId" value="Advanced" onclick="enableDeable()"style="display: ''"></td> 
                <td><input type="button" id="latiId" value="Latitude" style="display: none"></td>
                <td><input type="button" id="logId" value="Longitude" style="display: none"></td>
                <td><input type="button" id="backId" value="BackTo" style="display: none" onclick="backeToAdvance()"></td>
            </tr>
        </table>
    </div>
    <script>

var map;
function initMap() {
  map = new google.maps.Map(document.getElementById('map'), {
    center: {lat: -34.397, lng: 150.644},
    zoom: 8
  });
}
 function enableDeable(){
     document.getElementById("advancedId").style.display='none';
     document.getElementById("logId").style.display='inline';
     document.getElementById("latiId").style.display='inline';
     document.getElementById("backId").style.display='inline';
 }
  function backeToAdvance(){
     document.getElementById("advancedId").style.display='inline';
     document.getElementById("logId").style.display='none';
     document.getElementById("latiId").style.display='none';
     document.getElementById("backId").style.display='none';
 }
    </script>
    <script src="https://maps.googleapis.com/maps/api/js?key=AIzaSyBKjkalryNVHjVsG7BBalMn0NgDJYeOjPQ&callback=initMap"
        async defer></script>
  </body>
</html>