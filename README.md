# Bouquet - Jetpack Compose PDF reader

Bouquet is a PDF reader library written completely in Jetpack Compose, this was created using [PDFRender](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer) and coroutine.
The library supports both horizontal and vertical viewing.

## Features

- Different sources available out of box (Base64, URL, URI)
- Pinch to zoom and pan
- You can retrieve the open file to share
- Loading progress indication
- Current page and total page number
- Error handling

### How to

Step 1. Add `INTERNET` permissions on your AndroidManifest.xml

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

Step 2. Add the MavenCentral repository to your build file
Add it in your root build.gradle at the end of repositories:

	allprojects {
		repositories {
			mavenCentral()
		}
	}
Step 3. Add the dependency

	dependencies {
	        implementation 'io.github.grizzi91:bouquet:1.0.2'
	}

Step 4. You can use the library by creating the state in a Composable
This is for portrait viewing

```kotlin
val pdfState = rememberVerticalPdfReaderState(
	resource = ResourceType.Remote("https://myreport.altervista.org/Lorem_Ipsum.pdf"),
	isZoomEnable = true
)
```
This is for landscape viewing

```kotlin
val pdfState = rememberHorizontalPdfReaderState(
	resource = ResourceType.Remote("https://myreport.altervista.org/Lorem_Ipsum.pdf"),
	isZoomEnable = true
)
```

Or you can host the state in a ViewModel

```kotlin
class BouquetViewModel : ViewModel() {
  
    val pdfHorizontalReaderState = HorizontalPdfReaderState(
        resource = ResourceType.Remote("https://myreport.altervista.org/Lorem_Ipsum.pdf"),
	isZoomEnable = true
    )
    
    val pdfVerticallReaderState = VerticalPdfReaderState(
        resource = ResourceType.Remote("https://myreport.altervista.org/Lorem_Ipsum.pdf"),
	isZoomEnable = true
    )
}
```
Step 5. Then pass the state to the PDFReader function

```kotlin
VerticalPDFReader(
	state = pdfState,
	modifier = Modifier
		.fillMaxSize()
		.background(color = Color.Gray)
)
```

or

```kotlin
HorizontalPDFReader(
	state = pdfState,
	modifier = Modifier
		.fillMaxSize()
		.background(color = Color.Gray)
)
```

You can use Modifier to modify dimension, background color or shape and other parameter of the PDFReader view.

## Chose your source

You can choose different sources using the sealed class ResourceType

Remote source, with support for headers

```kotlin
ResourceType.Remote(
	url = "https://myreport.altervista.org/Lorem_Ipsum.pdf",
    headers = hashMapOf("headerKey" to "headerValue")
)
```
Local source

```kotlin
ResourceType.Local(
	// URI
)
```
Base64 source

```kotlin
ResourceType.Base64(
	// Base64 string
)
```

## Error handling

In case of errors, for example with the remote resource, you can recover the error from the state.

```kotlin
LaunchedEffect(key1 = pdfState.error) {
	pdfState.error?.let {
		// Show error
	}
}
```

## File sharing

After the opening of the pdf you can share it from the 'file' field of the state.

```kotlin
state.file?.let {
	FloatingActionButton(
		onClick = {
			shareFile(it)
		}
	) {
		Icon(
			painter = painterResource(id = android.R.drawable.ic_menu_share),
			contentDescription = "share"
		)
	}
}
```

## Sample app

For managing the download progress, current page number, total page number, scrolling status etc. you can refer to the sample app.

### License
```xml
   Copyright [2022] [Graziano Rizzi]

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```
