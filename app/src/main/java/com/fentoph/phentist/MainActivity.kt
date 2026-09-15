package com.fentoph.phentist

import android.Manifest
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

private val Navy=Color(0xFF0B1F33)
private val Teal=Color(0xFF08B39E)
private val Blue=Color(0xFF2563EB)
private val Ink=Color(0xFF101828)
private val Muted=Color(0xFF667085)
private val Page=Color(0xFFF8FAFC)

class MainActivity:ComponentActivity(){
    private val cameraPermission=registerForActivityResult(ActivityResultContracts.RequestPermission()){granted->SecurityState.cameraGranted=granted}
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE,android.view.WindowManager.LayoutParams.FLAG_SECURE);cameraPermission.launch(Manifest.permission.CAMERA);setContent{PhentistApp()}}
}
object SecurityState{var cameraGranted by mutableStateOf(false)}
data class UniversityUi(val id:String,val name:String,val location:String,val imageUrl:String?)

@Composable
fun PhentistApp(){
    val context=LocalContext.current
    val auth=remember{AuthRepository(context)}
    val google=remember{GoogleSignIn(context)}
    val scope=rememberCoroutineScope()
    val session by auth.session.collectAsState(initial=AuthSession(false,null,null,null,null))
    var universities by remember{mutableStateOf<List<UniversityUi>>(emptyList())}
    var search by remember{mutableStateOf("")}
    var loading by remember{mutableStateOf(true)}
    var message by remember{mutableStateOf<String?>(null)}
    var adminOpen by remember{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)}
    val isAdmin=session.email?.let{AdminPolicy.isAdmin(it)}==true
    fun loadUniversities(query:String){scope.launch{loading=true;PhentistApi.listUniversities(query).fold(onSuccess={list->universities=list.map{UniversityUi(it.id,it.name,it.location,it.imageUrl)};message=null},onFailure={message=it.message?:"Universitetlarni yuklab bo'lmadi."});loading=false}}
    LaunchedEffect(Unit){loadUniversities("")}

    MaterialTheme(colorScheme=androidx.compose.material3.lightColorScheme(primary=Teal,background=Page,surface=Color.White,onSurface=Ink,onBackground=Ink,outline=Color(0xFFD0D5DD))){
        Scaffold(containerColor=Page,topBar={TopAppBar(title={Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Brush.linearGradient(listOf(Teal,Blue))),contentAlignment=Alignment.Center){Text("P",color=Color.White,fontWeight=FontWeight.Bold)};Spacer(Modifier.size(9.dp));Text("Phentist",fontWeight=FontWeight.Bold,color=Navy)}},actions={if(isAdmin){OutlinedButton(onClick={adminOpen=!adminOpen},shape=RoundedCornerShape(10.dp)){Text(if(adminOpen) "Catalog" else "Admin")}}},colors=TopAppBarDefaults.topAppBarColors(containerColor=Color.White))}){padding->
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
                item{Spacer(Modifier.height(4.dp));Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Text("University Gallery",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,color=Ink);Text("Explore universities and discover successful learning paths.",style=MaterialTheme.typography.bodyMedium,color=Muted)}}
                if(!session.signedIn){item{Button(onClick={busy=true;scope.launch{google.signIn(context as ComponentActivity).fold(onSuccess={identity->PhentistApi.signInWithGoogle(identity.idToken).fold(onSuccess={result->auth.saveVerifiedGoogleIdentity(result.userId,result.email,result.displayName,result.accessToken);message="Google orqali tizimga kirdingiz."},onFailure={message=it.message?:"Kirish amalga oshmadi."})},onFailure={message=it.message?:"Kirish amalga oshmadi."});busy=false}},enabled=!busy,colors=ButtonDefaults.buttonColors(containerColor=Navy),shape=RoundedCornerShape(12.dp)){Text(if(busy)"Kutilmoqda..." else "Google orqali kirish")}}}
                item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){Surface(color=Teal,shape=RoundedCornerShape(20.dp)){Text("Universities",Modifier.padding(horizontal=14.dp,vertical=8.dp),color=Color.White,fontWeight=FontWeight.SemiBold)};Surface(color=Color.White,shape=RoundedCornerShape(20.dp),tonalElevation=1.dp){Text("Featured",Modifier.padding(horizontal=14.dp,vertical=8.dp),color=Ink)}}}
                item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){OutlinedTextField(value=search,onValueChange={search=it},modifier=Modifier.weight(1f),singleLine=true,label={Text("Search universities")},shape=RoundedCornerShape(14.dp));Button(onClick={loadUniversities(search.trim())},shape=RoundedCornerShape(14.dp)){Text("Search")}}}
                if(adminOpen&&isAdmin){item{AdminUniversityForm(enabled=!busy,onAdd={name,location,image->busy=true;scope.launch{PhentistApi.addUniversity(session.accessToken.orEmpty(),name,location,image).fold(onSuccess={message="University added successfully.";loadUniversities(search)},onFailure={message=it.message?:"University qo'shib bo'lmadi."});busy=false}}})}}
                if(loading){item{Box(Modifier.fillMaxWidth().padding(24.dp),contentAlignment=Alignment.Center){CircularProgressIndicator()}}}
                if(!loading&&universities.isEmpty()){item{Surface(Modifier.fillMaxWidth(),color=Color.White,shape=RoundedCornerShape(16.dp)){Text("No universities found.",Modifier.padding(22.dp),color=Muted)}}}
                items(universities,key={it.id}){university->UniversityCard(university,onDelete=if(isAdmin&&adminOpen){id->{busy=true;scope.launch{PhentistApi.removeUniversity(session.accessToken.orEmpty(),id).fold(onSuccess={message="University removed.";loadUniversities(search)},onFailure={message=it.message?:"Universityni o'chirib bo'lmadi."});busy=false}}}else null)}
                message?.let{item{Surface(Modifier.fillMaxWidth(),color=Color(0xFFEAF7F5),shape=RoundedCornerShape(14.dp)){Text(it,Modifier.padding(14.dp),color=Navy)}}}
                item{Spacer(Modifier.height(8.dp));Surface(Modifier.fillMaxWidth(),color=Color.White,shape=RoundedCornerShape(16.dp)){Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){Text("✓",color=Teal,fontWeight=FontWeight.Bold);Text(if(SecurityState.cameraGranted)"Protected reading is enabled." else "Camera permission is required for protected reading.",color=Muted,style=MaterialTheme.typography.bodySmall)}}}
            }
        }
    }
}

@Composable private fun UniversityCard(university:UniversityUi,onDelete:((String)->Unit)?){Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=Color.White),elevation=CardDefaults.cardElevation(defaultElevation=1.dp)){Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){RemoteImage(university.imageUrl,Modifier.size(94.dp).clip(RoundedCornerShape(12.dp)));Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(university.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,color=Ink);Text("⌖  ${university.location}",style=MaterialTheme.typography.bodyMedium,color=Muted)};if(onDelete!=null)OutlinedButton(onClick={onDelete(university.id)},shape=RoundedCornerShape(10.dp)){Text("Remove")}}}}

@Composable private fun RemoteImage(url:String?,modifier:Modifier){val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue=null,key1=url){value=if(url.isNullOrBlank())null else withContext(Dispatchers.IO){runCatching{URL(url).openStream().use{BitmapFactory.decodeStream(it)?.asImageBitmap()}}.getOrNull()}};if(bitmap!=null)Image(bitmap=bitmap!!,contentDescription=null,modifier=modifier,contentScale=ContentScale.Crop)else Box(modifier.background(Color(0xFFF2F4F7)),contentAlignment=Alignment.Center){Text("P",color=Color(0xFF98A2B3),fontWeight=FontWeight.Bold)}}

@Composable private fun AdminUniversityForm(enabled:Boolean,onAdd:(String,String,String)->Unit){var name by remember{mutableStateOf("")};var location by remember{mutableStateOf("")};var image by remember{mutableStateOf("")};Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("Admin · Add university",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text("Add the university name, location and image URL. The catalog is stored in Supabase.",style=MaterialTheme.typography.bodySmall,color=Muted);OutlinedTextField(name,{name=it},Modifier.fillMaxWidth(),enabled=enabled,label={Text("University name")},singleLine=true,shape=RoundedCornerShape(12.dp));OutlinedTextField(location,{location=it},Modifier.fillMaxWidth(),enabled=enabled,label={Text("Location")},singleLine=true,shape=RoundedCornerShape(12.dp));OutlinedTextField(image,{image=it},Modifier.fillMaxWidth(),enabled=enabled,label={Text("Image URL")},singleLine=true,shape=RoundedCornerShape(12.dp));Button(enabled=enabled&&name.isNotBlank()&&location.isNotBlank()&&image.isNotBlank(),onClick={onAdd(name.trim(),location.trim(),image.trim())},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){Text("Add to gallery",fontWeight=FontWeight.SemiBold)}}}}
