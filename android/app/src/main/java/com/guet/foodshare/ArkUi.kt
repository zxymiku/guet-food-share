package com.guet.foodshare

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.*

/** Original native interpretation of the Ark black/white/cyan information shell. */
object ArkUi {
    const val family = "ark"
    const val depth = "maximal"
    val ink = Color.rgb(16,20,22)
    val panel = Color.rgb(26,33,37)
    val paper = Color.rgb(244,246,246)
    val muted = Color.rgb(179,191,196)
    val signal = Color.rgb(24,209,255)
    val line = Color.rgb(64,79,86)
    fun dp(c:Context,n:Int)=(n*c.resources.displayMetrics.density).toInt()
    fun surface(fill:Int=panel,stroke:Int=line)=GradientDrawable().apply{setColor(fill);setStroke(1,stroke);cornerRadius=0f}
    fun column(c:Context,padding:Int=0)=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(c,padding),dp(c,padding),dp(c,padding),dp(c,padding))}
    fun label(c:Context,value:String,size:Float=14f,color:Int=paper)=TextView(c).apply{
        text=value;textSize=size;setTextColor(color);setPadding(dp(c,8),dp(c,7),dp(c,8),dp(c,7));setLineSpacing(dp(c,3).toFloat(),1f)
    }
    fun kicker(c:Context,value:String)=label(c,value,11f,muted).apply{typeface=Typeface.MONOSPACE;letterSpacing=.12f}
    fun button(c:Context,value:String,primary:Boolean=false,action:()->Unit)=Button(c).apply{
        text=value;textSize=14f;isAllCaps=false;minHeight=dp(c,48);minimumHeight=dp(c,48)
        setTextColor(if(primary)ink else paper);setPadding(dp(c,14),dp(c,8),dp(c,14),dp(c,8))
        background=RippleDrawable(ColorStateList.valueOf(Color.argb(70,24,209,255)),surface(if(primary)signal else panel,if(primary)signal else line),null)
        setOnClickListener{action()}
    }
    fun field(c:Context,hintText:String="",secret:Boolean=false)=EditText(c).apply{
        hint=hintText;textSize=16f;setTextColor(paper);setHintTextColor(muted);setSingleLine();minHeight=dp(c,50)
        background=surface();setPadding(dp(c,12),dp(c,10),dp(c,12),dp(c,10))
        if(secret){inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS}
    }
    fun rule(c:Context,accent:Boolean=false)=View(c).apply{setBackgroundColor(if(accent)signal else line);layoutParams=LinearLayout.LayoutParams(-1,dp(c,if(accent)3 else 1))}
    fun reveal(view:View){
        if(!ValueAnimator.areAnimatorsEnabled())return
        view.alpha=0f;view.translationX=dp(view.context,12).toFloat()
        view.animate().alpha(1f).translationX(0f).setDuration(240).start()
    }
    fun section(c:Context,index:String,title:String,subtitle:String)=column(c).apply{
        addView(kicker(c,index));addView(label(c,title,28f).apply{typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD)})
        addView(label(c,subtitle,14f,muted));addView(rule(c,true))
    }
}

/** Sparse blueprint grouping for the unconfigured stage. It is never a fake map. */
class AtlasStage(context:Context):View(context){
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas:Canvas){
        canvas.drawColor(ArkUi.ink);paint.strokeWidth=1f;paint.color=Color.rgb(29,40,46)
        val unit=ArkUi.dp(context,48).toFloat();var x=0f
        while(x<width){canvas.drawLine(x,0f,x,height.toFloat(),paint);x+=unit}
        var y=0f;while(y<height){canvas.drawLine(0f,y,width.toFloat(),y,paint);y+=unit}
        paint.color=ArkUi.line;canvas.drawLine(width*.68f,0f,width.toFloat(),height*.35f,paint)
        paint.color=ArkUi.signal;paint.strokeWidth=ArkUi.dp(context,2).toFloat()
        val edge=ArkUi.dp(context,18).toFloat();val arm=ArkUi.dp(context,24).toFloat()
        canvas.drawLine(edge,edge,edge+arm,edge,paint);canvas.drawLine(edge,edge,edge,edge+arm,paint)
        canvas.drawLine(width-edge,height-edge,width-edge-arm,height-edge,paint);canvas.drawLine(width-edge,height-edge,width-edge,height-edge-arm,paint)
    }
}
