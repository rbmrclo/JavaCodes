/*
 * BillsClock.java - 27 Nov 1998 - Version 1.05
 *(formerly javex.java)
 *
 * Copyright 1996-98 by Bill Giel
 *
 * E-mail: bgiel@ct2.nai.net
 * WWW: http://www.nai.net/~rvdi/~bgiel/bill.htm
 *
 *
 * Revision 1.01 - revised hms class to calculate GMT. This permits the clock
 * 10 Feb 96       to be used to display the time at any location by supplying
 *                 a TIMEZONE parameter in the HTML call.
 *
 * Revision 1.02 - revised timezone to accept real numbers, for places like
 * 11 Feb 96       India, with a 5.5 hour time difference. I learn something
 *                 new everyday!
 *
 * Revision 1.03 - fixed loop in run() to exit if clockThread==null, rather
 *                 than simple for(;;)
 *
 * Revision 1.04 - renamed file and applet class to billsClock; added
 * 12 Jun 96       parameter LOCALONLY for displaying viewer's local time
 *
 * Revision 1.05 - changed TZ algebraic sign to conform with astronomic convention
 * 28 Nov 98       
 *
 *
 *
 * Permission to use, copy, modify, and distribute this software
 * and its documentation for NON-COMMERCIAL or COMMERCIAL purposes and
 * without fee is hereby granted, provided that any use properly credits
 * the author, i.e. "Bill's Clock courtesy of <A HREF="mailto:bgiel@ct2.nai.net">
 * Moondog Software</A>.
 *
 *
 * THE AUTHOR MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY
 * OF THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. THE AUTHOR SHALL NOT BE LIABLE
 * FOR ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 *
 */

import java.awt.*;
import java.applet.*;
import java.util.*;
import java.net.*;

class Hms extends Date
{
    //Note that localOffset is hours difference from GMT
    //west of Greenwich meridian is negative, east is positive.
    //i.e. New York City (Eastern Standard Time) is -5
    //     Eastern Daylight Time is -4

    public Hms(double localOffset){
        super();
        long tzOffset=getTimezoneOffset()*60L*1000L;
        localOffset *= 3600000.0;
        setTime(getTime() + tzOffset + (long)localOffset);
    }

    public Hms(){
        super();
    }

    public double get_hours()
    {
        return (double)super.getHours()+(double)getMinutes()/60.0;
    }
}

abstract class ClockHand
{
    protected int baseX[], baseY[];
    protected int transX[],transY[];
    protected int numberOfPoints;

    public ClockHand(int originX, int originY, int length,int thickness,int points){
        baseX= new int[points]; baseY=new int[points];
        transX= new int[points]; transY=new int[points];
        initiallizePoints(originX,originY,length,thickness);
        numberOfPoints=points;
    }

    abstract protected void initiallizePoints(  int originX,
                                                int originY,
                                                int length,
                                                int thickness);

    abstract public void draw(Color color, double angle, Graphics g);

    protected void transform(double angle)
    {
        for(int i=0;i<numberOfPoints;i++){
            transX[i]=(int)(    (baseX[0]-baseX[i]) * Math.cos(angle) -
                                (baseY[0]-baseY[i]) * Math.sin(angle) +
                                 baseX[0]);

            transY[i]=(int)(    (baseX[0]-baseX[i]) * Math.sin(angle) +
                                (baseY[0]-baseY[i]) * Math.cos(angle) +
                                 baseY[0]);
        }
    }
}

class SweepHand extends ClockHand
{
    public SweepHand(int originX,int originY, int length, int points)
    {
        super(originX,originY,length,0,points);
	}

    protected void initiallizePoints(int originX,int originY, int length, int unused)
    {
        unused=unused;  //We don't use the thickness parameter in this class
                        //This comes from habit to prevent compiler warning
                        //concerning unused arguments.

        baseX[0]=originX; baseY[0]=originY;
        baseX[1]=originX; baseY[1]=originY-length/5;
        baseX[2]=originX; baseY[2]=originY+length;
    }

    public void draw(Color color, double angle, Graphics g)
    {
        transform(angle);
        g.setColor(color);
        g.drawLine(transX[1],transY[1],transX[2],transY[2]);
    }
}

class HmHand extends ClockHand
{
    public HmHand(int originX,int originY, int length,int thickness, int points){
        super(originX,originY,length,thickness,points);
    }

    protected void initiallizePoints(   int originX,
                                        int originY,
                                        int length,
                                        int thickness)
    {
        baseX[0]=originX;
        baseY[0]=originY;

        baseX[1]=baseX[0]-thickness/2;
        baseY[1]=baseY[0]+thickness/2;

        baseX[2]=baseX[1];
        baseY[2]=baseY[0]+length- thickness;

        baseX[3]=baseX[0];
        baseY[3]=baseY[0]+length;

        baseX[4]=baseX[0]+thickness/2;
        baseY[4]=baseY[2];

        baseX[5]=baseX[4];
        baseY[5]=baseY[1];
    }

    public void draw(Color color,double angle, Graphics g)
    {
        transform(angle);
        g.setColor(color);
        g.fillPolygon(transX,transY,numberOfPoints);
    }
}

public class BillsClock extends Applet implements Runnable
{
    //some DEFINE'd constants
    static final int BACKGROUND=0;              //Background image index
    static final int LOGO=1;                    //Logo image index
    static final String JAVEX="J***X";          //Default text on clock face
    static final double MINSEC=0.104719755;     //Radians per minute or second
    static final double HOUR=0.523598776;       //Radians per hour

    Thread clockThread = null;

    //User options, see getParameterInfo(), below.
    int width = 100;
    int height = 100;
    Color bgColor = new Color(0,0,0);
    Color faceColor = new Color(0,0,0);
    Color sweepColor = new Color(255,0,0);
    Color minuteColor = new Color (192,192,192);
    Color hourColor = new Color (255,255,255);
    Color textColor = new Color (255,255,255);
    Color caseColor = new Color (0,0,0);
    Color trimColor = new Color (192,192,192);
    String logoString=null;

    Image images[] = new Image[2]; //Array to hold optional images

    boolean isPainted=false; //Force painting on first update, if not painted

    //Center point of clock
    int x1,y1;

    //Array of points for triangular icon at 12:00
    int xPoints[]=new int[3], yPoints[]=new int[3];

    //Class to hold time, with method to return (double)(hours + minutes/60)
    Hms cur_time;

    //The clock's seconds, minutes, and hours hands.
    SweepHand sweep;
    HmHand  minuteHand,
            hourHand;

    //The last parameters used to draw the hands.
    double lastHour;
    int lastMinute,lastSecond;

    //The font used for text and date.
    Font font;

    //Offscreen image and device context, for buffered output.
    Image offScrImage;
    Graphics offScrGC;

    // Use to test background image, if any.
    MediaTracker tracker;


    int minDimension;   // Ensure a round clock if applet is not square.
    int originX;        // Upper left corner of a square enclosing the clock
    int originY;        // with respect to applet area.

    double tzDifference=0;

    boolean localOnly=false;


    //Users' parameters - self-explanatory?
    public String[][] getParameterInfo()
    {
        String[][] info = {
            {"width",       "int",      "width of the applet, in pixels"},
            {"height",      "int",      "height of the applet, in pixels"},
            {"bgColor",     "string",   "hex color triplet of the background, i.e. 000000 for black <black>"},
            {"faceColor",   "string",   "hex color triplet of clock face, i.e. 000000 for black <black>"},
            {"sweepColor",  "string",   "hex color triplet of seconds hand, i.e. FF0000 for red <red>"},
            {"minuteColor", "string",   "hex color triplet of minutes hand, i.e. C0C0C0 for lt.gray <lt.gray>"},
            {"hourColor",   "string",   "hex color triplet of hours hand, i.e. FFFFFF for white <white>"},
            {"textColor",   "string",   "hex color triplet of numbers, etc., i.e. FFFFFF for white <white>"},
            {"caseColor",   "string",   "hex color triplet of case, i.e. 000000 for black <black>"},
            {"trimColor",   "string",   "hex color triplet of case outliners, i.e. C0C0C0 for lt.gray <lt.gray>"},
            {"bgImageURL",  "string",   "URL of background image, if any <null>"},
            {"logoString",  "string",   "Name to display on watch face <JAVEX>"},
            {"logoImageURL","string",   "URL of logo image to display on watch face <null>"},
            {"timezone",    "real",     "Timezone difference from GMT (decimal hours,- West/+ East)<0>"},
            {"localonly",   "int",      "Non-zero will cause clock to display current local time <0>"}
        };
        return info;
    }

    //Applet name, author and info lines
    public String getAppletInfo()
    {
        return "billsClock 1.05 (C) 1996-98 by Bill Giel<bgiel@ct2.nai.net>";
    }

    void showURLerror(Exception e)
    {
        String errorMsg = "JAVEX URL error: "+e;
        showStatus(errorMsg);
        System.err.println(errorMsg);
    }

    // This lets us create clocks of various sizes, but with the same
    // proportions.
    private int size(int percent)
    {
        return (int)((double)percent/100.0 * (double)minDimension);
    }

    public void init()
    {
        URL imagesURL[] = new URL[2];
        String szImagesURL[] = new String[2];
        tracker = new MediaTracker(this);

        String paramString    = getParameter( "WIDTH"  );
        if( paramString != null )
            width = Integer.valueOf(paramString).intValue();

        paramString   = getParameter( "HEIGHT" );
        if( paramString != null )
            height = Integer.valueOf(paramString).intValue();

       paramString   = getParameter( "TIMEZONE" );
        if( paramString != null )
            tzDifference = Double.valueOf(paramString).doubleValue();

       paramString  =   getParameter( "LOCALONLY" );
        if( paramString != null && Integer.valueOf(paramString).intValue() != 0){
                localOnly=true;
                tzDifference=0.;
        }

        paramString    = getParameter( "BGCOLOR");
        if( paramString != null )
            bgColor=parseColorString(paramString);

        paramString    = getParameter( "FACECOLOR");
        if( paramString != null )
            faceColor=parseColorString(paramString);

        paramString    = getParameter( "SWEEPCOLOR");
        if( paramString != null )
            sweepColor=parseColorString(paramString);

        paramString    = getParameter( "MINUTECOLOR");
        if( paramString != null )
            minuteColor=parseColorString(paramString);

        paramString    = getParameter( "HOURCOLOR");
        if( paramString != null )
            hourColor=parseColorString(paramString);

        paramString    = getParameter( "TEXTCOLOR");
        if( paramString != null )
            textColor=parseColorString(paramString);

        paramString    = getParameter( "CASECOLOR");
        if( paramString != null )
            caseColor=parseColorString(paramString);

        paramString    = getParameter( "TRIMCOLOR");
        if( paramString != null )
            trimColor=parseColorString(paramString);

        logoString  = getParameter( "LOGOSTRING");
        if( logoString == null )
            logoString=JAVEX;
        else if(logoString.length() > 8)
            logoString= logoString.substring(0,8); //Max 8 characters!

        szImagesURL[BACKGROUND]  = getParameter("BGIMAGEURL");
        szImagesURL[LOGO] = getParameter("LOGOIMAGEURL");

        for(int i=0; i<2; i++){
            if(szImagesURL[i] != null){
                try{
                    imagesURL[i]=new URL(getCodeBase(),szImagesURL[i]);
                } catch (MalformedURLException e)
                    {
                        showURLerror(e);
                        imagesURL[i]=null;
                        images[i]=null;
                    }
                if(imagesURL[i] != null){
                    showStatus("Javex loading image: " + imagesURL[i].toString());
                    images[i]=getImage(imagesURL[i]);
                    if(images[i] != null)
                        tracker.addImage(images[i],i);
                    showStatus("");
                }
                try{
                    tracker.waitForAll(i);
                }catch (InterruptedException e){}
            }
            else images[i]=null;
        }

        cur_time=(localOnly)? new Hms() : new Hms(tzDifference);
        lastHour=-1.0;
        lastMinute=-1;
        lastSecond=-1;

        x1=width/2;
        y1=height/2;

        minDimension= Math.min(width, height);
        originX=(width-minDimension)/2;
        originY=(height-minDimension)/2;

        xPoints[1]=x1-size(3); xPoints[2]=x1+size(3); xPoints[0]=x1;
        yPoints[1]=y1-size(38);yPoints[2]=y1-size(38); yPoints[0]=y1-size(27);

        sweep=new SweepHand(x1,y1,size(40),3);
        minuteHand=new HmHand(x1,y1,size(40),size(6),6);
        hourHand=new HmHand(x1,y1,size(25),size(8),6);

        font=new Font("TXT",Font.BOLD,size(10));

        offScrImage = createImage(width,height);
        offScrGC = offScrImage.getGraphics();

        System.out.println(getAppletInfo());
    }

    public void start()
    {
        if(clockThread == null){
            clockThread = new Thread(this);
        }
        clockThread.start();        
    }

    public void stop()
    {
        clockThread = null;
    }

    private void drawHands(Graphics g)
    {

        double angle;
        int i,j;
        int x,y;

        angle=MINSEC * lastSecond;
        sweep.draw(faceColor, angle, g);

        if(cur_time.getMinutes() != lastMinute){
            minuteHand.draw(faceColor,MINSEC*lastMinute,g);
            if(cur_time.get_hours() != lastHour)
                hourHand.draw(faceColor,HOUR*lastHour,g);
        }

        g.setColor(textColor);
        g.fillRect(originX+size(12),y1-size(2),size(10),size(4));
        g.fillRect(x1-size(2),originY + minDimension-size(22),size(4),size(10));
        g.fillPolygon( xPoints, yPoints, 3);
        for(i=1;i<12;i+=3)
            for(j=i;j<i+2;j++){
                x=(int)(x1+Math.sin(HOUR*j)*size(35));
                y=(int)(y1-Math.cos(HOUR*j)*size(35));
                g.fillOval(x-size(3),y-size(3),size(6),size(6));
            }

        //Set the font and get font info...
        g.setFont(font);
        FontMetrics fm=g.getFontMetrics();

        //Paint our logo...
        g.drawString(logoString,x1-fm.stringWidth(logoString)/2,y1-size(12));

        //Get the day of the month...
        String day=Integer.toString(cur_time.getDate(),10);

        //Paint it...
        g.drawString(   day,
                        originX + minDimension-size(14)-fm.stringWidth(day),
                        y1+size(5));

        //and put a box around it.
        g.drawRect( originX + minDimension-size(14)-fm.stringWidth(day)-size(2),
                    y1-size(5)-size(2),
                    fm.stringWidth(day)+size(4),
                    size(10)+size(4));

        if(images[LOGO] != null){
            x = originX + (minDimension-images[LOGO].getWidth(this))/2;
            y = y1 + (minDimension/2 - size(22) - images[LOGO].getHeight(this))/2;
            if(x > 0 && y > 0)
                offScrGC.drawImage(images[LOGO], x, y, this);
        }

        lastHour=cur_time.get_hours();
        hourHand.draw(hourColor,HOUR*lastHour,g);

        lastMinute=cur_time.getMinutes();
        minuteHand.draw(minuteColor,MINSEC*lastMinute,g);

        g.setColor(minuteColor);
        g.fillOval(x1-size(4),y1-size(4),size(8),size(8));
        g.setColor(sweepColor);
        g.fillOval(x1-size(3),y1-size(3),size(6),size(6));

        lastSecond=cur_time.getSeconds();
        angle=MINSEC*lastSecond;
        sweep.draw(sweepColor, angle,g);

        g.setColor(trimColor);
        g.fillOval(x1-size(1),y1-size(1),size(2),size(2));
    }

    private Color parseColorString(String colorString)
    {
        if(colorString.length()==6){
            int R = Integer.valueOf(colorString.substring(0,2),16).intValue();
            int G = Integer.valueOf(colorString.substring(2,4),16).intValue();
            int B = Integer.valueOf(colorString.substring(4,6),16).intValue();
            return new Color(R,G,B);
        }
        else return Color.lightGray;
    }

    public void run()
    {
        repaint();
        while(null != clockThread){
            cur_time= (localOnly)? new Hms() :new Hms(tzDifference);
            repaint();

            try{
                Thread.sleep(500);
            } catch (InterruptedException e) {}
        }

    }

    public void paint(Graphics g)
    {
        int i,x0,y0,x2,y2;

        if(images[BACKGROUND] == null){
            offScrGC.setColor(bgColor);
            offScrGC.fillRect(0,0,width,height);
        }
        else
            offScrGC.drawImage(images[BACKGROUND], 0, 0, this);

        offScrGC.setColor(caseColor);

        //Shrink one pixel so we don't clip anything off...
        offScrGC.fillOval(  originX+1,
                            originY+1,
                            minDimension-2,
                            minDimension-2);

        offScrGC.setColor(faceColor);
        offScrGC.fillOval(  originX + size(5),
                            originY + size(5),
                            minDimension - size(10),
                            minDimension - size(10));

        offScrGC.setColor(trimColor);
        offScrGC.drawOval(  originX+1,
                            originY+1,
                            minDimension-2,
                            minDimension-2);

        offScrGC.drawOval(  originX + size(5),
                            originY + size(5),
                            minDimension - size(10),
                            minDimension - size(10));

        offScrGC.setColor(textColor);

        //Draw graduations, a longer index every fifth mark...
        for(i=0;i<60;i++){
            if(i==0 || (i>=5 && i%5 == 0)){
                x0=(int)(x1+size(40)*Math.sin(MINSEC*i));
                y0=(int)(y1+size(40)*Math.cos(MINSEC*i));
            }
            else{
                x0=(int)(x1+size(42)*Math.sin(MINSEC*i));
                y0=(int)(y1+size(42)*Math.cos(MINSEC*i));
            }
            x2=(int)(x1+size(44)*Math.sin(MINSEC*i));
            y2=(int)(y1+size(44)*Math.cos(MINSEC*i));
            offScrGC.drawLine(x0,y0,x2,y2);
        }

        drawHands(offScrGC);
        g.drawImage(offScrImage,0,0,this);

        isPainted=true;
    }

    public synchronized void update(Graphics g)
    {
        if(!isPainted)
            paint(g);
        else{
            drawHands(offScrGC);
            g.drawImage(offScrImage,0,0,this);
        }
    }
}
