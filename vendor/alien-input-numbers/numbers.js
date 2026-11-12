/* Native radix-seven numerals. Existing F IDs are never reassigned.
 * N{coefficient}{P|N}{power} denotes a productive bar numeral absent from
 * the finite bitmap inventory, not a newly claimed author dictionary entry.
 */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.MFA_NUMBERS=api;})(globalThis,function(){
'use strict';
const DIGITS=['F1301','F1302','F1310','F1317','F1324','F1331','F1338'];
const ROWS={
 1:{0:'F1302',1:'F1304',2:'F1305',3:'F1306','-1':'F1307','-2':'F1308','-3':'F1309'},
 2:{0:'F1310',1:'F1311',2:'F1313',3:'F1314','-1':'F1312','-2':'F1315','-3':'F1316'},
 3:{0:'F1317',1:'F1318',2:'F1320',3:'F1321','-1':'F1319','-2':'F1322','-3':'F1323'},
 4:{0:'F1324',1:'F1325',2:'F1327',3:'F1328','-1':'F1326','-2':'F1329','-3':'F1330'},
 5:{0:'F1331',1:'F1332',2:'F1334',3:'F1335','-1':'F1333','-2':'F1336','-3':'F1337'},
 6:{0:'F1338',1:'F1339',2:'F1341',3:'F1342','-1':'F1340','-2':'F1343','-3':'F1344'}
};
const VALUES=new Map([[DIGITS[0],{d:0,e:0}]]);
for(const [d,row] of Object.entries(ROWS))for(const [e,id] of Object.entries(row))VALUES.set(id,{d:Number(d),e:Number(e)});
const NEG='F0098',DIV='F0438',ADD='F0096',SUB='F0097',SCALE='F0515',APPROX='F0538';
const MAX_POWER=2048,MAX_INPUT=1024;
function abs(n){return n<0n?-n:n;}
function gcd(a,b){a=abs(a);b=abs(b);while(b){const t=a%b;a=b;b=t;}return a||1n;}
function rational(n,d=1n){n=BigInt(n);d=BigInt(d);if(!d)throw Error('分母不能为零。');if(d<0n){n=-n;d=-d;}const g=gcd(n,d);return {n:n/g,d:d/g};}
function pow7(e){if(!Number.isInteger(e)||e<0||e>MAX_POWER)throw Error('七的幂超出支持范围。');return 7n**BigInt(e);}
function scale(x,e){if(!Number.isInteger(e)||Math.abs(e)>MAX_POWER)throw Error('指数超出支持范围。');return e>=0?rational(x.n*pow7(e),x.d):rational(x.n,x.d*pow7(-e));}
function parse7(text,{signed=true,empty=0n}={}){
 let s=String(text).trim();if(!s)return empty;if(s.length>MAX_INPUT)throw Error('数字太长。');
 const negative=s.startsWith('-');if(negative)s=s.slice(1);if(!/^[0-6]+$/.test(s)||negative&&!signed)throw Error('本土数字只使用零到六。');
 let n=0n;for(const c of s)n=n*7n+BigInt(c);return negative?-n:n;
}
function decimal(text){
 const s=String(text).trim();if(s.length>MAX_INPUT)throw Error('数字太长。');
 const m=s.match(/^([+-]?)(\d*)(?:\.(\d*))?(?:[eE]([+-]?\d+))?$/);
 if(!m||!m[2]&&!m[3])throw Error('无法识别十进制数字。');
 const e=Number(m[4]||0)-(m[3]||'').length;if(!Number.isInteger(e)||Math.abs(e)>MAX_POWER)throw Error('指数超出支持范围。');
 const n=BigInt((m[2]||'0')+(m[3]||''))*(m[1]==='-'?-1n:1n);
 return e>=0?rational(n*10n**BigInt(e)):rational(n,10n**BigInt(-e));
}
function parseEarth(text){
 let s=String(text).trim();if(s.length>MAX_INPUT)throw Error('数字太长。');
 const sci=s.match(/^(.*?)\s*(?:\*|×)\s*7\s*\^\s*([+-]?\d+)$/);
 if(sci)return scale(parseEarth(sci[1]),Number(sci[2]));
 const mixed=s.match(/^([+-]?\d+)\s+(\d+)\s*\/\s*(\d+)$/);
 if(mixed){const w=BigInt(mixed[1]),p=BigInt(mixed[2]),q=BigInt(mixed[3]);return rational((abs(w)*q+p)*(mixed[1].startsWith('-')?-1n:1n),q);}
 const parts=s.split('/');if(parts.length===2){const a=decimal(parts[0]),b=decimal(parts[1]);return rational(a.n*b.d,a.d*b.n);}if(parts.length>2)throw Error('分数格式不合法。');
 return decimal(s);
}
function isToken(id){if(typeof id!=='string')return false;const m=id.match(/^N([1-6])([PN])(\d{1,4})$/);return !!m&&Number(m[3])>=4&&Number(m[3])<=MAX_POWER&&!/^0/.test(m[3]);}
function component(d,e){if(!Number.isInteger(d)||d<1||d>6||!Number.isInteger(e)||Math.abs(e)>MAX_POWER)throw Error('数词构件不合法。');return ROWS[d][e]||'N'+d+(e>=0?'P':'N')+Math.abs(e);}
function tokenValue(id){
 let v=VALUES.get(id);if(!v&&isToken(id)){const m=id.match(/^N([1-6])([PN])(\d+)$/);v={d:Number(m[1]),e:Number(m[3])*(m[2]==='N'?-1:1)};}
 if(!v)return null;return v.e>=0?rational(BigInt(v.d)*pow7(v.e)):rational(BigInt(v.d),pow7(-v.e));
}
function encodeInteger(value){
 const n=BigInt(value);if(n===0n)return [DIGITS[0]];const digits=abs(n).toString(7);if(digits.length-1>MAX_POWER)throw Error('数字超出数词支持范围。');
 const out=n<0n?[NEG]:[];for(let i=0;i<digits.length;i++){const d=Number(digits[i]);if(d)out.push(component(d,digits.length-1-i));}return out;
}
function finite7(x){let q=x.d,k=0;while(q%7n===0n&&q>1n){q/=7n;k++;}return q===1n?k:null;}
function encodeFinite(x){
 const k=finite7(x);if(k===null)return null;let n=abs(x.n),whole=n/x.d,rem=n%x.d;
 const out=x.n<0n?[NEG]:[];if(whole)out.push(...encodeInteger(whole));
 for(let e=1;e<=k;e++){rem*=7n;const digit=Number(rem/x.d);rem%=x.d;if(digit)out.push(component(digit,-e));}
 if(!out.length||out.length===1&&out[0]===NEG)out.push(DIGITS[0]);return out;
}
function fraction(x){return [...encodeInteger(x.n),DIV,...encodeInteger(x.d)];}
function encode(x,format='auto'){
 x=rational(x.n,x.d);const finite=encodeFinite(x);
 if(format==='fraction')return {tokens:x.d===1n?encodeInteger(x.n):fraction(x),exact:true,notation:'fraction',value:x};
 if(format==='mixed'&&x.d!==1n){
  if(finite)return {tokens:finite,exact:true,notation:'mixed-native',value:x};
  const whole=abs(x.n)/x.d,rem=abs(x.n)%x.d;
  if(!rem)return {tokens:encodeInteger(x.n/x.d),exact:true,notation:'integer',value:x};
  const tokens=[...encodeInteger(x.n<0n?-rem:rem),DIV,...encodeInteger(x.d)];
  // Game arithmetic is left-to-right. r/q + w, never w+r/q.
  if(whole)tokens.push(x.n<0n?SUB:ADD,...encodeInteger(whole));
  return {tokens,exact:true,notation:'mixed-expression',value:x};
 }
 if(finite)return {tokens:finite,exact:true,notation:x.d===1n?'integer':'finite-seven',value:x};
 return {tokens:fraction(x),exact:true,notation:'fraction',nonTerminating:true,value:x};
}
function exponent7(x){
 if(x.n===0n)return 0;let e=abs(x.n).toString(7).length-x.d.toString(7).length;
 if(Math.abs(e)>MAX_POWER)throw Error('指数超出支持范围。');
 const cmp=e=>e>=0?abs(x.n)-x.d*pow7(e):abs(x.n)*pow7(-e)-x.d;
 while(cmp(e)<0n){if(e<=-MAX_POWER)throw Error('指数超出支持范围。');e--;}while(e<MAX_POWER&&cmp(e+1)>=0n)e++;return e;
}
function scientific(x,precision=4){
 x=rational(x.n,x.d);if(!Number.isInteger(precision)||precision<1||precision>32)throw Error('有效数位应为1至32。');
 if(!x.n)return {tokens:[DIGITS[0]],exact:true,notation:'scientific',value:x,rounded:x,exponent:0};
 let e=exponent7(x);const m=scale(rational(abs(x.n),x.d),-e),factor=pow7(precision-1),scaled=m.n*factor;
 let q=scaled/m.d;const rem=scaled%m.d,nextDigit=Number(rem*7n/m.d);
 // Attested game rule: if the next base-seven digit is not <4, increment.
 if(nextDigit>=4)q++;
 if(q>=pow7(precision)){q/=7n;e++;}
 const coefficient=rational(x.n<0n?-q:q,factor),rounded=scale(coefficient,e),exact=rounded.n*x.d===x.n*rounded.d;
 return {tokens:[...(exact?[]:[APPROX]),...encodeFinite(coefficient),SCALE,...encodeInteger(e)],
  exact,notation:'scientific',value:x,rounded,exponent:e,precision,rounding:'native next base-seven digit >= 4'};
}
function fromFields(mode,fields){
 const f=id=>fields[id]||'',coefficient=()=>{const w=parse7(f('whole')),digits=f('digits').replace(/^-/,'');const den=pow7(digits.length);return rational((abs(w)*den+parse7(digits,{signed:false}))*(f('whole').startsWith('-')?-1n:1n),den);};
 if(mode==='integer')return rational(parse7(f('integer')));
 if(mode==='radix')return coefficient();
 if(mode==='fraction')return rational(parse7(f('numerator')),parse7(f('denominator')));
 if(mode==='mixed'){
  const w=parse7(f('whole')),n=abs(parse7(f('numerator'))),d=abs(parse7(f('denominator')));
  return rational((abs(w)*d+n)*(f('whole').startsWith('-')?-1n:1n),d);
 }
 if(mode==='scientific')return scale(coefficient(),Number(parse7(f('exponent'))));
 throw Error('未知数字格式。');
}
function label(id){const v=tokenValue(id);return v?(v.d===1n?String(v.n):v.n+'/'+v.d):null;}
function svg(id,basePng){
 if(!isToken(id)||!/^data:image\/png;base64,/.test(basePng))throw Error('生成数词的参数不合法。');
 const m=id.match(/^N([1-6])([PN])(\d+)$/),count=Number(m[3]),top=m[2]==='P',extra=(count-1)*10,height=100+extra;
 const y=top?extra:0,lines=Array.from({length:count},(_,i)=>`<path d="M6 ${top?6+i*10:94+i*10}H94"/>`).join('');
 return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 ${height}"><image href="${basePng}" x="0" y="${y}" width="100" height="100"/><g fill="none" stroke="#111" stroke-width="2">${lines}</g></svg>`;
}
function imageData(id,basePng){return 'data:image/svg+xml;charset=utf-8,'+encodeURIComponent(svg(id,basePng));}
return {DIGITS,ROWS,NEG,DIV,ADD,SUB,SCALE,APPROX,MAX_POWER,rational,parse7,parseEarth,component,isToken,tokenValue,encodeInteger,encode,scientific,fromFields,label,svg,imageData,finite7,scale};
});
