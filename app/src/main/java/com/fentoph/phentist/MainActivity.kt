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
import androidx.compose.ui.draw.blur
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
import java.text.NumberFormat
import java.util.Locale

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
enum class GalleryScreen{HOME,UNIVERSITY,ESSAY}

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
    var screen by remember{mutableStateOf(GalleryScreen.HOME)}
    var selectedUniversity by remember{mutableStateOf<UniversityUi?>(null)}
    var selectedEssay by remember{mutableStateOf<PhentistApi.Essay?>(null)}
    val isAdmin=session.email?.let{AdminPolicy.isAdmin(it)}==true

    fun loadUniversities(query:String){scope.launch{loading=true;PhentistApi.listUniversities(query).fold(onSuccess={list->universities=list.map{UniversityUi(it.id,it.name,it.location,it.imageUrl)};message=null},onFailure={message=it.message?:"Universities could not be loaded."});loading=false}}
    LaunchedEffect(Unit){loadUniversities("")}

    MaterialTheme(colorScheme=lightColorScheme(primary=Teal,background=Page,surface=Color.White,onSurface=Ink,onBackground=Ink,outline=Color(0xFFD0D5DD))){
        when {
            screen==GalleryScreen.ESSAY && selectedEssay!=null -> EssayDetailsScreen(essay=selectedEssay!!,universityName=selectedUniversity?.name.orEmpty(),onBack={screen=GalleryScreen.UNIVERSITY},message=message,clearMessage={message=null})
            screen==GalleryScreen.UNIVERSITY && selectedUniversity!=null -> UniversityDetailScreen(university=selectedUniversity!!,isAdmin=isAdmin,accessToken=session.accessToken,onBack={screen=GalleryScreen.HOME},onOpenEssay={essay->selectedEssay=essay;message=null;screen=GalleryScreen.ESSAY},onMessage={message=it},message=message)
            else -> GalleryHome(session=session,google=google,auth=auth,universities=universities,search=search,onSearchChange={search=it},onSearch={loadUniversities},loading=loading,message=message,busy=busy,isAdmin=isAdmin,adminOpen=adminOpen,onAdminToggle={adminOpen=!adminOpen},onAddUniversity={name,location,image->busy=true;scope.launch{PhentistApi.addUniversity(session.accessToken.orEmpty(),name,location,image).fold(onSuccess={message="University added successfully.";loadUniversities(search)},onFailure={message=it.message?:"University could not be added."});busy=false}},onDeleteUniversity={id->busy=true;scope.launch{PhentistApi.removeUniversity(session.accessToken.orEmpty(),id).fold(onSuccess={message="University removed.";loadUniversities(search)},onFailure={message=it.message?:"University could not be removed."});busy=false}},onOpenUniversity={u->selectedUniversity=u;message=null;screen=GalleryScreen.UNIVERSITY})
        }
    }
}

@Composable
private fun GalleryHome(session:AuthSession,google:GoogleSignIn,auth:AuthRepository,universities:List<UniversityUi>,search:String,onSearchChange:(String)->Unit,onSearch:(String)->Unit,loading:Boolean,message:String?,busy:Boolean,isAdmin:Boolean,adminOpen:Boolean,onAdminToggle:()->Unit,onAddUniversity:(String,String,String)->Unit,onDeleteUniversity:(String)->Unit,onOpenUniversity:(UniversityUi)->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    Scaffold(containerColor=Page,topBar={TopAppBar(title={Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Brush.linearGradient(listOf(Teal,Blue))),contentAlignment=Alignment.Center){Text("P",color=Color.White,fontWeight=FontWeight.Bold)};Spacer(Modifier.size(9.dp));Text("Phentist",fontWeight=FontWeight.Bold,color=Navy)}},actions={if(isAdmin){OutlinedButton(onClick=onAdminToggle,shape=RoundedCornerShape(10.dp)){Text(if(adminOpen)"Catalog" else "Admin")}}},colors=TopAppBarDefaults.topAppBarColors(containerColor=Color.White))}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            item{Spacer(Modifier.height(4.dp));Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Text("University Gallery",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,color=Ink);Text("Explore universities and discover successful learning paths.",style=MaterialTheme.typography.bodyMedium,color=Muted)}}
            if(!session.signedIn){item{Button(onClick={scope.launch{google.signIn(context as ComponentActivity).fold(onSuccess={identity->PhentistApi.signInWithGoogle(identity.idToken).fold(onSuccess={result->auth.saveVerifiedGoogleIdentity(result.userId,result.email,result.displayName,result.accessToken)},onFailure={message="Google sign-in failed."})},onFailure={message="Google sign-in failed."})}},enabled=!busy,colors=ButtonDefaults.buttonColors(containerColor=Navy),shape=RoundedCornerShape(12.dp)){Text("Sign in with Google")}}}
            item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Surface(color=Teal,shape=RoundedCornerShape(20.dp)){Text("Universities",Modifier.padding(horizontal=14.dp,vertical=8.dp),color=Color.White,fontWeight=FontWeight.SemiBold)};Surface(color=Color.White,shape=RoundedCornerShape(20.dp),tonalElevation=1.dp){Text("Featured",Modifier.padding(horizontal=14.dp,vertical=8.dp),color=Ink)}}}
            item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){OutlinedTextField(value=search,onValueChange=onSearchChange,modifier=Modifier.weight(1f),singleLine=true,label={Text("Search universities")},shape=RoundedCornerShape(14.dp));Button(onClick={onSearch(search.trim())},shape=RoundedCornerShape(14.dp)){Text("Search")}}}
            if(adminOpen&&isAdmin){item{AdminUniversityForm(enabled=!busy,onAdd=onAddUniversity)}}
            if(loading){item{Box(Modifier.fillMaxWidth().padding(24.dp),contentAlignment=Alignment.Center){CircularProgressIndicator()}}}
            if(!loading&&universities.isEmpty()){item{Surface(Modifier.fillMaxWidth(),color=Color.White,shape=RoundedCornerShape(16.dp)){Text("No universities found.",Modifier.padding(22.dp),color=Muted)}}}
            items(universities,key={it.id}){university->UniversityCard(university,onClick={onOpenUniversity(university)},onDelete=if(isAdmin&&adminOpen)onDeleteUniversity else null)}
            message?.let{item{Surface(Modifier.fillMaxWidth(),color=Color(0xFFEAF7F5),shape=RoundedCornerShape(14.dp)){Text(it,Modifier.padding(14.dp),color=Navy)}}}
            item{Spacer(Modifier.height(8.dp));Surface(Modifier.fillMaxWidth(),color=Color.White,shape=RoundedCornerShape(16.dp)){Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){Text("✓",color=Teal,fontWeight=FontWeight.Bold);Text(if(SecurityState.cameraGranted)"Protected reading is enabled." else "Camera permission is required for protected reading.",color=Muted,style=MaterialTheme.typography.bodySmall)}}}
        }
    }
}

@Composable private fun UniversityCard(university:UniversityUi,onClick:()->Unit,onDelete:((String)->Unit)?){Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=Color.White),elevation=CardDefaults.cardElevation(defaultElevation=1.dp),onClick=onClick){Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){RemoteImage(university.imageUrl,Modifier.size(94.dp).clip(RoundedCornerShape(12.dp)));Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(university.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,color=Ink);Text("⌖  ${university.location}",style=MaterialTheme.typography.bodyMedium,color=Muted)};if(onDelete!=null)OutlinedButton(onClick={onDelete(university.id)},shape=RoundedCornerShape(10.dp)){Text("Remove")}}}}

@Composable
private fun UniversityDetailScreen(university:UniversityUi,isAdmin:Boolean,accessToken:String?,onBack:()->Unit,onOpenEssay:(PhentistApi.Essay)->Unit,onMessage:(String)->Unit,message:String?){
    val scope=rememberCoroutineScope()
    var essays by remember(university.id){mutableStateOf<List<PhentistApi.Essay>>(emptyList())}
    var loading by remember(university.id){mutableStateOf(true)}
    var adminOpen by remember{mutableStateOf(false)}
    fun reload(){scope.launch{PhentistApi.listEssays(university.id).fold(onSuccess={essays=it},onFailure={onMessage(it.message?:"Questions could not be loaded.")})}}
    LaunchedEffect(university.id){loading=true;PhentistApi.listEssays(university.id).fold(onSuccess={essays=it},onFailure={onMessage(it.message?:"Questions could not be loaded.")});loading=false}
    Scaffold(containerColor=Page,topBar={TopAppBar(title={Text("University")},navigationIcon={IconButton(onClick=onBack){Text("‹",style=MaterialTheme.typography.headlineMedium,color=Navy)}},actions={if(isAdmin){OutlinedButton(onClick={adminOpen=!adminOpen},shape=RoundedCornerShape(10.dp)){Text(if(adminOpen)"Close" else "Admin")}}},colors=TopAppBarDefaults.topAppBarColors(containerColor=Color.White))}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),verticalArrangement=Arrangement.spacedBy(14.dp)){
            item{Column(Modifier.padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){RemoteImage(university.imageUrl,Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(18.dp)));Text(university.name,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,color=Ink);Text(university.location,style=MaterialTheme.typography.bodyLarge,color=Muted)}}
            item{Column(Modifier.padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Text("Essay Questions",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,color=Ink);Text("Explore prompts and sample responses from this university.",style=MaterialTheme.typography.bodyMedium,color=Muted)}}
            if(adminOpen&&isAdmin){item{AdminEssayForm(enabled=true,onAdd={question,wordLimit,preview,fullText,author,avatar,price->scope.launch{PhentistApi.addEssay(accessToken.orEmpty(),university.id,question,wordLimit,preview,fullText,author,avatar,price).fold(onSuccess={onMessage("Question added successfully.");reload()},onFailure={onMessage(it.message?:"Question could not be added.")})}}})}}
            if(loading){item{Box(Modifier.fillMaxWidth().padding(28.dp),contentAlignment=Alignment.Center){CircularProgressIndicator()}}}
            if(!loading&&essays.isEmpty()){item{Surface(Modifier.fillMaxWidth().padding(horizontal=16.dp),color=Color.White,shape=RoundedCornerShape(16.dp)){Text("No essay questions have been added yet.",Modifier.padding(22.dp),color=Muted)}}}
            items(essays,key={it.id}){essay->EssayListCard(essay,onClick={onOpenEssay(essay)},onDelete=if(isAdmin&&adminOpen){ {scope.launch{PhentistApi.removeEssay(accessToken.orEmpty(),essay.id).fold(onSuccess={onMessage("Question removed.");reload()},onFailure={onMessage(it.message?:"Question could not be removed.")})}}}else null)}
            message?.let{item{Surface(Modifier.padding(horizontal=16.dp).fillMaxWidth(),color=Color(0xFFEAF7F5),shape=RoundedCornerShape(14.dp)){Text(it,Modifier.padding(14.dp),color=Navy)}}}
            item{Spacer(Modifier.height(12.dp))}
        }
    }
}

@Composable private fun EssayListCard(essay:PhentistApi.Essay,onClick:()->Unit,onDelete:(()->Unit)?){Card(Modifier.fillMaxWidth().padding(horizontal=16.dp),shape=RoundedCornerShape(15.dp),colors=CardDefaults.cardColors(containerColor=Color.White),elevation=CardDefaults.cardElevation(defaultElevation=1.dp),onClick=onClick){Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp)){Text("Question: ${essay.question} (${essay.wordLimit} words)",style=MaterialTheme.typography.titleMedium,color=Color(0xFF25405F),fontWeight=FontWeight.Medium);if(essay.previewText.isNotBlank())Text(essay.previewText,maxLines=2,style=MaterialTheme.typography.bodyMedium,color=Muted)};Column(horizontalAlignment=Alignment.End){Text(formatPrice(essay.priceMinor,essay.currency),color=Teal,fontWeight=FontWeight.Bold);if(onDelete!=null)OutlinedButton(onClick=onDelete,shape=RoundedCornerShape(9.dp)){Text("Remove")}}}}}

@Composable
private fun EssayDetailsScreen(essay:PhentistApi.Essay,universityName:String,onBack:()->Unit,message:String?,clearMessage:()->Unit){
    Scaffold(containerColor=Color.White,topBar={TopAppBar(title={Text("Essay Details",fontWeight=FontWeight.Bold)},navigationIcon={TextButton(onClick=onBack){Text("←  Back",color=Navy)}},colors=TopAppBarDefaults.topAppBarColors(containerColor=Color.White))}){padding->
        Row(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(22.dp)){
            Column(Modifier.weight(2.3f).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text(universityName,style=MaterialTheme.typography.titleMedium,color=Muted)
                Card(Modifier.fillMaxWidth().weight(1f),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color.White),border=ButtonDefaults.outlinedButtonBorder){Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
                    Text("PROMPT",style=MaterialTheme.typography.labelLarge,color=Color(0xFF7A8EA3));Text("Question: ${essay.question} (${essay.wordLimit} words)",style=MaterialTheme.typography.titleMedium,color=Ink)
                    Text("RESPONSE",style=MaterialTheme.typography.labelLarge,color=Color(0xFF7A8EA3))
                    Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center){
                        Text(essay.fullText.ifBlank{essay.previewText.ifBlank{"The full essay response will appear here."}},style=MaterialTheme.typography.bodyLarge,color=Color(0xFF667085),lineHeight=28.sp,modifier=Modifier.fillMaxWidth().blur(8.dp))
                        Surface(color=Teal,shape=RoundedCornerShape(28.dp),shadowElevation=5.dp){Row(Modifier.padding(horizontal=22.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){Text("▣",color=Color.White);Text(formatPrice(essay.priceMinor,essay.currency),color=Color.White,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)}}
                    }
                }}
            }
            Column(Modifier.width(330.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
                Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color.White),border=ButtonDefaults.outlinedButtonBorder){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){RemoteImage(essay.authorAvatarUrl,Modifier.size(72.dp).clip(RoundedCornerShape(50.dp)));Column{Text(essay.authorName,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,color=Ink);Text(universityName,color=Muted)}}}
                OutlinedButton(onClick={},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp)){Text("♡   Add to favorites")}
                InsightCard("🔎  Why this essay worked",listOf("Blends personal storytelling with concrete, university-specific details","Shows initiative through campus engagement and research connections","Clearly links interests to future goals"))
                InsightCard("💡  What you can learn",listOf("Start with personal values and connect them to the university","Use vivid campus details to show genuine interest","Mention specific programs and opportunities that align with your goals","Demonstrate how you will contribute to the community"))
            }
        }
    }
}

@Composable private fun InsightCard(title:String,bullets:List<String>){Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFFF7F8FA)),border=ButtonDefaults.outlinedButtonBorder){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Text(title,fontWeight=FontWeight.Bold,color=Ink);bullets.forEach{Text("• $it",style=MaterialTheme.typography.bodySmall,color=Muted)}}}}

@Composable private fun RemoteImage(url:String?,modifier:Modifier){val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue=null,key1=url){value=if(url.isNullOrBlank())null else withContext(Dispatchers.IO){runCatching{URL(url).openStream().use{BitmapFactory.decodeStream(it)?.asImageBitmap()}}.getOrNull()}};if(bitmap!=null)Image(bitmap=bitmap!!,contentDescription=null,modifier=modifier,contentScale=ContentScale.Crop)else Box(modifier.background(Color(0xFFF2F4F7)),contentAlignment=Alignment.Center){Text("P",color=Color(0xFF98A2B3),fontWeight=FontWeight.Bold)}}

@Composable private fun AdminUniversityForm(enabled:Boolean,onAdd:(String,String,String)->Unit){var name by remember{mutableStateOf("")};var location by remember{mutableStateOf("")};var image by remember{mutableStateOf("")};Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("Admin · Add university",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);OutlinedTextField(name,{name=it},Modifier.fillMaxWidth(),enabled=enabled,label={Text("University name")},singleLine=true,shape=RoundedCornerShape(12.dp));OutlinedTextField(location,{location=it},Modifier.fillMaxWidth(),enabled=enabled,label={Text("Location")},singleLine=true,shape=RoundedCornerShape(12.dp));OutlinedTextField(image,{image=it},Modifier.fillMaxWidth(),enabled=enabled,label={Text("Image URL")},singleLine=true,shape=RoundedCornerShape(12.dp));Button(enabled=enabled&&name.isNotBlank()&&location.isNotBlank()&&image.isNotBlank(),onClick={onAdd(name.trim(),location.trim(),image.trim())},shape=RoundedCornerShape(12.dp)){Text("Add to gallery")}}}}

@Composable private fun AdminEssayForm(enabled:Boolean,onAdd:(String,Int,String,String,String,String,Long)->Unit){var question by remember{mutableStateOf("")};var wordLimit by remember{mutableStateOf("650")};var preview by remember{mutableStateOf("")};var fullText by remember{mutableStateOf("")};var author by remember{mutableStateOf("")};var avatar by remember{mutableStateOf("")};var price by remember{mutableStateOf("25000")};Card(Modifier.fillMaxWidth().padding(horizontal=16.dp),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("Admin · Add essay question",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);OutlinedTextField(question,{question=it},Modifier.fillMaxWidth(),enabled=enabled,label={Text("Question / prompt")},shape=RoundedCornerShape(12.dp));Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){OutlinedTextField(wordLimit,{wordLimit=it.filter(Char::isDigit)},Modifier.weight(1f),enabled=enabled,label={Text("Word limit")},singleLine=true,shape=RoundedCornerShape(12.dp));OutlinedTextField(price,{price=it.filter(Char::isDigit)},Modifier.weight(1f),enabled=enabled,label={Text("Price (UZS)")},singleLine=true,shape=RoundedCornerShape(12.dp))};OutlinedTextField(preview,{preview=it},Modifier.fillMaxWidth(),enabled=enabled,label={Text("Preview text")},minLines=3,shape=RoundedCornerShape(12.dp));OutlinedTextField(fullText,{fullText=it},Modifier.fillMaxWidth(),enabled=enabled,label={Text("Full essay response")},minLines=6,shape=RoundedCornerShape(12.dp));OutlinedTextField(author,{author=it},Modifier.fillMaxWidth(),enabled=enabled,label={Text("Author name")},singleLine=true,shape=RoundedCornerShape(12.dp));OutlinedTextField(avatar,{avatar=it},Modifier.fillMaxWidth(),enabled=enabled,label={Text("Author avatar URL")},singleLine=true,shape=RoundedCornerShape(12.dp));Button(enabled=enabled&&question.isNotBlank()&&(price.toLongOrNull()?:0)>=0,onClick={onAdd(question.trim(),wordLimit.toIntOrNull()?:650,preview.trim(),fullText,author.ifBlank{"Phentist"}.trim(),avatar.trim(),price.toLongOrNull()?:25000)},shape=RoundedCornerShape(12.dp)){Text("Add question")}}}}

private fun formatPrice(amount:Long,currency:String):String{val formatted=NumberFormat.getNumberInstance(Locale.US).format(amount);return "$formatted $currency"}
