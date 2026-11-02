#include <jni.h>
#include <aaudio/AAudio.h>
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <math.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <time.h>
#include <dlfcn.h>
#include <android/log.h>

#define JNI(name) Java_com_yang_player_audio_NativeAudio_##name
#define TAPS 64
#define PHASES 1024
#define PI 3.14159265358979323846
static int sample_bytes(int encoding) { return encoding == 2 ? 2 : encoding == 21 ? 3 : encoding == 3 ? 1 : 4; }
typedef struct { double b0,b1,b2,a1,a2; } Coeff;
typedef struct {
    int in_rate,in_channels,in_encoding,out_rate,out_channels,out_encoding;
    double phase;
    int64_t received;
    double history[TAPS][2];
    float kernel[PHASES][TAPS];
    Coeff current[10], target[10];
    double z1[10][2],z2[10][2];
    double gain,target_gain;
    int limiter,dither;
    uint32_t random;
} Dsp;
static double read_sample(const uint8_t *p, int encoding) {
    if(encoding==4) { float v; memcpy(&v,p,4); return isfinite(v)?v:0; }
    if(encoding==2) { int16_t v; memcpy(&v,p,2); return v/32768.0f; }
    if(encoding==3) return ((int)p[0]-128)/128.0f;
    if(encoding==21) { int32_t v=(int32_t)((uint32_t)p[0]<<8 | (uint32_t)p[1]<<16 | (uint32_t)p[2]<<24); return v/2147483648.0f; }
    int32_t v; memcpy(&v,p,4); return v/2147483648.0;
}
static double random01(Dsp *d) { d->random^=d->random<<13; d->random^=d->random>>17; d->random^=d->random<<5; return d->random/4294967296.0; }
static void store_sample(Dsp *d,uint8_t *p,double v) {
    if(!isfinite(v))v=0;
    if(d->out_encoding==4){float f=(float)fmax(-1.0,fmin(1.0,v));memcpy(p,&f,4);return;}
    int bits=d->out_encoding==2?16:d->out_encoding==21?24:32;
    double scale=bits==16?32768.0:bits==24?8388608.0:2147483648.0;
    // Triangular dither is applied only when quantizing to an integer container.
    int64_t q=(int64_t)llrint(fmax(-scale,fmin(scale-1,v*scale+(d->dither?random01(d)-random01(d):0))));
    for(int b=0;b<bits/8;b++)p[b]=(uint8_t)((uint64_t)q>>(8*b));
}
JNIEXPORT jlong JNICALL JNI(create)(JNIEnv *env,jobject obj,jint ir,jint ic,jint ie,jint rate,jint channels,jint encoding) {
    (void)env;(void)obj;
    if(ir<8000||ir>768000||ic<1||ic>8||rate<8000||rate>192000||channels<1||channels>2)return 0;
    Dsp *d=calloc(1,sizeof(*d));if(!d)return 0;
    d->in_rate=ir;d->in_channels=ic;d->in_encoding=ie;d->out_rate=rate;d->out_channels=channels;d->out_encoding=encoding;d->gain=d->target_gain=1;d->random=0x83a6c517;
    for(int b=0;b<10;b++)d->current[b].b0=d->target[b].b0=1;
    double cutoff=fmin(1.0,(double)rate/ir)*0.94;
    for(int phase=0;phase<PHASES;phase++) {
        double frac=(double)phase/PHASES,sum=0;
        for(int t=0;t<TAPS;t++) {
            double x=(t-31)-frac;
            double sinc=fabs(x)<1e-10?cutoff:sin(PI*x*cutoff)/(PI*x);
            double window=0.42+0.5*cos(PI*x/32)+0.08*cos(2*PI*x/32);
            d->kernel[phase][t]=(float)(sinc*window);sum+=sinc*window;
        }
        for(int t=0;t<TAPS;t++)d->kernel[phase][t]/=(float)sum;
    }
    return (jlong)(intptr_t)d;
}
JNIEXPORT void JNICALL JNI(destroy)(JNIEnv *e,jobject o,jlong handle){(void)e;(void)o;free((void*)(intptr_t)handle);}
JNIEXPORT void JNICALL JNI(dsp)(JNIEnv *env,jobject obj,jlong handle,jfloatArray frequencies,jfloatArray gains,jfloatArray qs,jfloat preamp,jboolean eq,jboolean headroom,jboolean limiter,jfloat volume) {
    (void)obj; Dsp *d=(Dsp*)(intptr_t)handle;if(!d)return;
    float f[10],g[10],q[10];
    if((*env)->GetArrayLength(env,frequencies)!=10||(*env)->GetArrayLength(env,gains)!=10||(*env)->GetArrayLength(env,qs)!=10)return;
    (*env)->GetFloatArrayRegion(env,frequencies,0,10,f);(*env)->GetFloatArrayRegion(env,gains,0,10,g);(*env)->GetFloatArrayRegion(env,qs,0,10,q);
    double margin=0;
    for(int b=0;b<10;b++) {
        Coeff c={1,0,0,0,0};
        if(eq&&f[b]<d->out_rate*0.48) {
            // RBJ peaking biquad, double-precision state, per-channel delay lines.
            double A=pow(10,g[b]/40.0),w=2*PI*f[b]/d->out_rate,alpha=sin(w)/(2*fmax(0.3,q[b]));
            double a0=1+alpha/A;
            c=(Coeff){(1+alpha*A)/a0,(-2*cos(w))/a0,(1-alpha*A)/a0,(-2*cos(w))/a0,(1-alpha/A)/a0};
            margin=fmax(margin,g[b]);
        }
        d->target[b]=c;
    }
    d->target_gain=pow(10,(preamp-(headroom?margin:0))/20.0)*fmax(0,fmin(1,volume));
    if(volume<=0)d->gain=0;
    d->limiter=limiter&&(eq||preamp>0);
    d->dither=eq||preamp!=0||volume!=1||d->in_rate!=d->out_rate||sample_bytes(d->out_encoding)<sample_bytes(d->in_encoding);
}
static double channel_sample(Dsp *d,const uint8_t *input,int frame,int channel) {
    int width=sample_bytes(d->in_encoding),ic=d->in_channels;
    const uint8_t *base=input+(size_t)frame*ic*width;
    if(ic==1)return read_sample(base,d->in_encoding);
    if(ic<=2)return read_sample(base+channel*width,d->in_encoding);
    // ITU-style stereo downmix with bounded gain for multichannel sources.
    double value=read_sample(base+channel*width,d->in_encoding);
    value+=0.70710678*read_sample(base+2*width,d->in_encoding);
    if(ic>3)value+=0.5*read_sample(base+3*width,d->in_encoding);
    for(int c=4+channel;c<ic;c+=2)value+=0.70710678*read_sample(base+c*width,d->in_encoding);
    return value/2.91421356;
}
JNIEXPORT jint JNICALL JNI(process)(JNIEnv *env,jobject obj,jlong handle,jobject input,jint offset,jint frames,jobject output) {
    (void)obj; Dsp *d=(Dsp*)(intptr_t)handle;if(!d||frames<0||frames>2048)return -1;
    uint8_t *in=(*env)->GetDirectBufferAddress(env,input),*out=(*env)->GetDirectBufferAddress(env,output);
    jlong in_size=(*env)->GetDirectBufferCapacity(env,input),capacity=(*env)->GetDirectBufferCapacity(env,output);
    int in_bytes=sample_bytes(d->in_encoding)*d->in_channels,out_bytes=sample_bytes(d->out_encoding)*d->out_channels;
    if(!in||!out||offset<0||(int64_t)offset+(int64_t)frames*in_bytes>in_size)return -1;
    in+=offset;
    int64_t end=d->received+frames; int produced=0;
    double step=(double)d->in_rate/d->out_rate;
    while(d->phase<end) {
        if((int64_t)(produced+1)*out_bytes>capacity)return -1;
        double samples[2]={0,0};
        if(d->in_rate==d->out_rate) {
            int index=(int)(d->phase-d->received);
            for(int c=0;c<d->out_channels;c++)samples[c]=channel_sample(d,in,index,c);
        } else {
            int64_t index=(int64_t)floor(d->phase);
            int ph=(int)((d->phase-index)*PHASES);if(ph<0)ph=0;if(ph>=PHASES)ph=PHASES-1;
            for(int t=0;t<TAPS;t++) {
                int relative=(int)(index-62+t-d->received);
                for(int c=0;c<d->out_channels;c++) {
                    double value=relative>=0&&relative<frames?channel_sample(d,in,relative,c):relative<0&&relative>=-TAPS?d->history[TAPS+relative][c]:0;
                    samples[c]+=value*d->kernel[ph][t];
                }
            }
        }
        d->gain+=(d->target_gain-d->gain)*0.002;
        for(int b=0;b<10;b++) {
            double *v=(double*)&d->current[b],*target=(double*)&d->target[b];
            for(int k=0;k<5;k++)v[k]+=(target[k]-v[k])*0.001;
        }
        for(int c=0;c<d->out_channels;c++) {
            double x=samples[c]*d->gain;
            for(int b=0;b<10;b++) { Coeff a=d->current[b];double y=a.b0*x+d->z1[b][c];d->z1[b][c]=a.b1*x-a.a1*y+d->z2[b][c];d->z2[b][c]=a.b2*x-a.a2*y;x=y; }
            if(d->limiter&&fabs(x)>0.95)x=copysign(0.95+0.05*tanh((fabs(x)-0.95)/0.05),x);
            store_sample(d,out+(size_t)produced*out_bytes+c*sample_bytes(d->out_encoding),x);
        }
        produced++;d->phase+=step;
    }
    for(int i=0;i<TAPS;i++) for(int c=0;c<d->out_channels;c++) {
        int relative=frames-TAPS+i;
        d->history[i][c]=relative>=0?channel_sample(d,in,relative,c):d->history[TAPS+relative][c];
    }
    d->received=end;
    return produced*out_bytes;
}

typedef struct {
    int backend,rate,channels,encoding,buffer_frames,exclusive;
    AAudioStream *aa;
    SLObjectItf sl_engine,sl_mix,sl_player;
    SLEngineItf engine;
    SLPlayItf play;
    SLAndroidSimpleBufferQueueItf queue;
    pthread_mutex_t mutex;
    uint8_t *blocks[32];
    int lengths[32],first,count,block_bytes,closing;
    uint64_t completed,submitted;
    int underruns;
} Output;
static void queue_done(SLAndroidSimpleBufferQueueItf queue,void *context) {
    (void)queue;Output *o=context;pthread_mutex_lock(&o->mutex);
    if(!o->closing&&o->count>0){o->completed+=o->lengths[o->first]/(o->channels*2);o->first=(o->first+1)%32;o->count--;}
    pthread_mutex_unlock(&o->mutex);
}
static void output_close(Output *o) {
    if(!o)return;
    if(o->aa){AAudioStream_requestStop(o->aa);AAudioStream_close(o->aa);}
    if(o->sl_player){if(o->play)(*o->play)->SetPlayState(o->play,SL_PLAYSTATE_STOPPED);pthread_mutex_lock(&o->mutex);o->closing=1;pthread_mutex_unlock(&o->mutex);(*o->sl_player)->Destroy(o->sl_player);}
    if(o->sl_mix)(*o->sl_mix)->Destroy(o->sl_mix);
    if(o->sl_engine)(*o->sl_engine)->Destroy(o->sl_engine);
    for(int i=0;i<32;i++)free(o->blocks[i]);
    pthread_mutex_destroy(&o->mutex);free(o);
}
JNIEXPORT jlong JNICALL JNI(openOutput)(JNIEnv *env,jobject obj,jint backend,jint rate,jint channels,jint encoding,jint buffer_ms,jint device,jboolean exclusive) {
    (void)env;(void)obj;
    if(rate<8000||rate>192000||channels<1||channels>2||buffer_ms<40||buffer_ms>800)return 0;
    Output *o=calloc(1,sizeof(*o));if(!o)return 0;
    pthread_mutex_init(&o->mutex,0);o->backend=backend;o->rate=rate;o->channels=channels;o->encoding=encoding;
    if(backend==1) {
        AAudioStreamBuilder *builder=0;
        if(AAudio_createStreamBuilder(&builder)!=AAUDIO_OK){output_close(o);return 0;}
        AAudioStreamBuilder_setDirection(builder,AAUDIO_DIRECTION_OUTPUT);
        // These setters start at API 28. Resolve them at runtime to retain API 26 support.
        typedef void (*SetAttribute)(AAudioStreamBuilder*,int32_t);
        SetAttribute set_usage=(SetAttribute)dlsym(RTLD_DEFAULT,"AAudioStreamBuilder_setUsage");
        SetAttribute set_content=(SetAttribute)dlsym(RTLD_DEFAULT,"AAudioStreamBuilder_setContentType");
        if(set_usage)set_usage(builder,1); // AAUDIO_USAGE_MEDIA
        if(set_content)set_content(builder,2); // AAUDIO_CONTENT_TYPE_MUSIC
        AAudioStreamBuilder_setChannelCount(builder,channels);AAudioStreamBuilder_setSampleRate(builder,rate);
        // I24/I32 were introduced at API 31; rejected formats are negotiated by Java.
        AAudioStreamBuilder_setFormat(builder,encoding==2?AAUDIO_FORMAT_PCM_I16:encoding==4?AAUDIO_FORMAT_PCM_FLOAT:encoding==21?AAUDIO_FORMAT_PCM_I24_PACKED:AAUDIO_FORMAT_PCM_I32);
        AAudioStreamBuilder_setSharingMode(builder,exclusive?AAUDIO_SHARING_MODE_EXCLUSIVE:AAUDIO_SHARING_MODE_SHARED);
        AAudioStreamBuilder_setPerformanceMode(builder,AAUDIO_PERFORMANCE_MODE_NONE);
        AAudioStreamBuilder_setBufferCapacityInFrames(builder,rate*buffer_ms/1000);
        if(device>0)AAudioStreamBuilder_setDeviceId(builder,device);
        aaudio_result_t result=AAudioStreamBuilder_openStream(builder,&o->aa);AAudioStreamBuilder_delete(builder);
        if(result!=AAUDIO_OK){output_close(o);return 0;}
        o->rate=AAudioStream_getSampleRate(o->aa);o->channels=AAudioStream_getChannelCount(o->aa);
        int format=AAudioStream_getFormat(o->aa);o->encoding=format==AAUDIO_FORMAT_PCM_I16?2:format==AAUDIO_FORMAT_PCM_FLOAT?4:format==AAUDIO_FORMAT_PCM_I24_PACKED?21:format==AAUDIO_FORMAT_PCM_I32?22:0;
        if(!o->encoding||o->channels!=channels){output_close(o);return 0;}
        AAudioStream_setBufferSizeInFrames(o->aa,o->rate*buffer_ms/1000);
        o->buffer_frames=AAudioStream_getBufferSizeInFrames(o->aa);o->exclusive=AAudioStream_getSharingMode(o->aa)==AAUDIO_SHARING_MODE_EXCLUSIVE;
    } else {
        if(encoding!=2||rate>48000){output_close(o);return 0;}
        if(slCreateEngine(&o->sl_engine,0,0,0,0,0)!=SL_RESULT_SUCCESS)goto fail;
        if((*o->sl_engine)->Realize(o->sl_engine,SL_BOOLEAN_FALSE)!=SL_RESULT_SUCCESS)goto fail;
        if((*o->sl_engine)->GetInterface(o->sl_engine,SL_IID_ENGINE,&o->engine)!=SL_RESULT_SUCCESS)goto fail;
        if((*o->engine)->CreateOutputMix(o->engine,&o->sl_mix,0,0,0)!=SL_RESULT_SUCCESS)goto fail;
        if((*o->sl_mix)->Realize(o->sl_mix,SL_BOOLEAN_FALSE)!=SL_RESULT_SUCCESS)goto fail;
        SLDataLocator_AndroidSimpleBufferQueue loc={SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE,32};
        SLDataFormat_PCM pcm={SL_DATAFORMAT_PCM,(SLuint32)channels,(SLuint32)rate*1000,SL_PCMSAMPLEFORMAT_FIXED_16,SL_PCMSAMPLEFORMAT_FIXED_16,
            channels==1?SL_SPEAKER_FRONT_CENTER:SL_SPEAKER_FRONT_LEFT|SL_SPEAKER_FRONT_RIGHT,SL_BYTEORDER_LITTLEENDIAN};
        SLDataSource source={&loc,&pcm};
        SLDataLocator_OutputMix mix={SL_DATALOCATOR_OUTPUTMIX,o->sl_mix};SLDataSink sink={&mix,0};
        SLInterfaceID ids[]={SL_IID_ANDROIDSIMPLEBUFFERQUEUE,SL_IID_ANDROIDCONFIGURATION};SLboolean required[]={SL_BOOLEAN_TRUE,SL_BOOLEAN_TRUE};
        if((*o->engine)->CreateAudioPlayer(o->engine,&o->sl_player,&source,&sink,2,ids,required)!=SL_RESULT_SUCCESS)goto fail;
        SLAndroidConfigurationItf config=0;
        if((*o->sl_player)->GetInterface(o->sl_player,SL_IID_ANDROIDCONFIGURATION,&config)==SL_RESULT_SUCCESS){SLint32 stream=SL_ANDROID_STREAM_MEDIA;(*config)->SetConfiguration(config,SL_ANDROID_KEY_STREAM_TYPE,&stream,sizeof(stream));}
        if((*o->sl_player)->Realize(o->sl_player,SL_BOOLEAN_FALSE)!=SL_RESULT_SUCCESS)goto fail;
        if((*o->sl_player)->GetInterface(o->sl_player,SL_IID_PLAY,&o->play)!=SL_RESULT_SUCCESS)goto fail;
        if((*o->sl_player)->GetInterface(o->sl_player,SL_IID_ANDROIDSIMPLEBUFFERQUEUE,&o->queue)!=SL_RESULT_SUCCESS)goto fail;
        if((*o->queue)->RegisterCallback(o->queue,queue_done,o)!=SL_RESULT_SUCCESS)goto fail;
        // Android creates the underlying track on the PAUSED/PLAYING transition.
        // Do not leave a newly realized player STOPPED while priming its queue.
        if((*o->play)->SetPlayState(o->play,SL_PLAYSTATE_PAUSED)!=SL_RESULT_SUCCESS)goto fail;
        o->buffer_frames=rate*buffer_ms/1000;o->block_bytes=(o->buffer_frames/16+1)*channels*2;
        for(int i=0;i<32;i++){o->blocks[i]=malloc(o->block_bytes);if(!o->blocks[i])goto fail;}
    }
    return (jlong)(intptr_t)o;
fail: output_close(o);return 0;
}
JNIEXPORT jintArray JNICALL JNI(outputInfo)(JNIEnv *env,jobject obj,jlong handle) {
    (void)obj;Output *o=(Output*)(intptr_t)handle;if(!o)return 0;
    int underruns=o->aa?AAudioStream_getXRunCount(o->aa):-1;
    int running=0;
    if(o->aa){aaudio_stream_state_t state=AAudioStream_getState(o->aa);running=state==AAUDIO_STREAM_STATE_STARTED?1:(state==AAUDIO_STREAM_STATE_DISCONNECTED||state==AAUDIO_STREAM_STATE_CLOSED)?-1:0;}
    else {SLuint32 state=0;if((*o->play)->GetPlayState(o->play,&state)!=SL_RESULT_SUCCESS)running=-1;else running=state==SL_PLAYSTATE_PLAYING?1:0;}
    jint values[]={o->rate,o->channels,o->encoding,o->buffer_frames,o->exclusive,underruns,o->aa?AAudioStream_getDeviceId(o->aa):0,running};
    jintArray result=(*env)->NewIntArray(env,8);(*env)->SetIntArrayRegion(env,result,0,8,values);return result;
}
JNIEXPORT jint JNICALL JNI(write)(JNIEnv *env,jobject obj,jlong handle,jobject buffer,jint offset,jint frames) {
    (void)obj;Output *o=(Output*)(intptr_t)handle;if(!o)return -1;
    uint8_t *data=(*env)->GetDirectBufferAddress(env,buffer);jlong capacity=(*env)->GetDirectBufferCapacity(env,buffer);
    int width=o->channels*sample_bytes(o->encoding);
    if(!data||offset<0||frames<0||(int64_t)offset+(int64_t)frames*width>capacity)return -1;
    if(o->aa){int count=AAudioStream_write(o->aa,data+offset,frames,0);if(count>0)o->submitted+=count;return count;}
    pthread_mutex_lock(&o->mutex);
    if(o->count>=16){pthread_mutex_unlock(&o->mutex);return 0;}
    int slot=(o->first+o->count)%32,bytes=frames*width;if(bytes>o->block_bytes)bytes=o->block_bytes;bytes-=bytes%width;
    memcpy(o->blocks[slot],data+offset,bytes);o->lengths[slot]=bytes;
    o->count++;
    SLresult result=(*o->queue)->Enqueue(o->queue,o->blocks[slot],bytes);
    if(result!=SL_RESULT_SUCCESS)o->count--;
    pthread_mutex_unlock(&o->mutex);
    if(result!=SL_RESULT_SUCCESS)return -1;
    o->submitted+=bytes/width;return bytes/width;
}
JNIEXPORT jint JNICALL JNI(playing)(JNIEnv *env,jobject obj,jlong handle,jboolean play) {
    (void)env;(void)obj;Output *o=(Output*)(intptr_t)handle;if(!o)return -1;
    int result;
    if(o->aa)result=play?AAudioStream_requestStart(o->aa):AAudioStream_requestPause(o->aa);
    else result=(*o->play)->SetPlayState(o->play,play?SL_PLAYSTATE_PLAYING:SL_PLAYSTATE_PAUSED)==SL_RESULT_SUCCESS?0:-1;
    __android_log_print(result<0?ANDROID_LOG_WARN:ANDROID_LOG_INFO,"YangAudio","%s %s: result=%d, rate=%d, encoding=%d",o->aa?"AAudio":"OpenSL",play?"start":"pause",result,o->rate,o->encoding);
    return result;
}
JNIEXPORT jlong JNICALL JNI(position)(JNIEnv *env,jobject obj,jlong handle) {
    (void)env;(void)obj;Output *o=(Output*)(intptr_t)handle;if(!o)return 0;
    int64_t position=0;
    if(o->aa){int64_t time=0;if(AAudioStream_getTimestamp(o->aa,CLOCK_MONOTONIC,&position,&time)!=AAUDIO_OK)position=AAudioStream_getFramesRead(o->aa);int64_t adjustment=AAudioStream_getFramesWritten(o->aa)-(int64_t)o->submitted;if(adjustment>0)position-=adjustment;}
    else {
        SLmillisecond ms=0;SLresult result=(*o->play)->GetPosition(o->play,&ms);
        pthread_mutex_lock(&o->mutex);
        int64_t completed=(int64_t)o->completed;
        int64_t limit=completed+(o->count>0?o->lengths[o->first]/(o->channels*2):0);
        position=result==SL_RESULT_SUCCESS&&ms!=SL_TIME_UNKNOWN?(int64_t)ms*o->rate/1000:completed;
        if(position<completed)position=completed;if(position>limit)position=limit;
        pthread_mutex_unlock(&o->mutex);
    }
    if(position<0)position=0;if((uint64_t)position>o->submitted)position=o->submitted;return position;
}
JNIEXPORT void JNICALL JNI(closeOutput)(JNIEnv *env,jobject obj,jlong handle){(void)env;(void)obj;output_close((Output*)(intptr_t)handle);}
