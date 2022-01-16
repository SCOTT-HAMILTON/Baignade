<p align="center">
      <a href="https://scott-hamilton.mit-license.org/"><img alt="MIT License" src="https://img.shields.io/badge/License-MIT-525252.svg?labelColor=292929&logo=creative%20commons&style=for-the-badge" /></a>
</p>
<h1 align="center">Baignade - Android Widget to visualize sea tides at a glance</h1>

## Description
Baignade lets you choose which port you want to follow and it will create a modern graph showing the past, current and next few tides for this specific location as well as the tide coefficient and the water temperature (in degrees of course XD).

Baignade (a swim in french) uses data provided by the French Naval Hydrographic and Oceanographic Service (Service Hydrographique et Océanographique de la Marine), the [SHOM](https://www.shom.fr/) in short.

The SHOM provides a wide range of ports, so big that we can't visualize them all without crashing your 4G ram device XD. So when trying to select a location on the map, don't get scared if you can't select anything, the ports are showing at your location only once you stopped zooming or scrolling for more than 2 seconds.


## Screens
**Configure Page**
![configure-lowres](https://user-images.githubusercontent.com/24496705/149673148-e7094b70-3eeb-4d72-9f9d-504358c278f5.gif)

**Final Configured Widget**
![final-lowres](https://user-images.githubusercontent.com/24496705/126784899-e725f207-66f2-4ab3-8607-75921816a251.jpg)

## Building
This project is configured with Android Studio

## Tricks Used
Android AppWidgets don't seem to support a lot of views, the list is available [here](https://developer.android.com/reference/android/widget/RemoteViews). Moreover, the Button View seems ugly looking. So what could I use to make a modern graph with such limitations ? The answer is ImageView. This view can't have styling issues because the only thing it shows is the provided image. Then you might ask how I draw my modern graph to a Bitmap image. The answer is I don't! I'm using the Material-Graph-Library which provides custom views for displaying graphs : I fill the view with the data, layout it and draw it to a bitmap !
PS: Nevertheless, I  had to customize the drawing implementation of Material-Graph-Library to fit the needs of the widget.

## License
Baignade is delivered as it is under the well known MIT License.

**References that helped**
 - [android documentation] : <https://developer.android.com/>
 - [Guide for building an android App Widget] : <https://developer.android.com/guide/topics/appwidgets>
 - [Material-Graph-Library from Velli20] : <https://github.com/Velli20/Material-Graph-Library>
 - [roller-coaster-builder from susanmclain] : <https://github.com/susanmclain/roller-coaster-builder>
 - [apiMareeInfo from saniho] : <https://github.com/saniho/apiMareeInfo>

[//]: # (These are reference links used in the body of this note and get stripped out when the markdown processor does its job. There is no need to format nicely because it shouldn't be seen. Thanks SO - http://stackoverflow.com/questions/4823468/store-comments-in-markdown-syntax)

   [android documentation]: <https://developer.android.com/>
   [Guide for building an android App Widget]: <https://developer.android.com/guide/topics/appwidgets>
   [Material-Graph-Library from Velli20]: <https://github.com/Velli20/Material-Graph-Library>
   [roller-coaster-builder from susanmclain]: <https://github.com/susanmclain/roller-coaster-builder>
   [apiMareeInfo from saniho]: <https://github.com/saniho/apiMareeInfo>
