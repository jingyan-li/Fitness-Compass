package ch.ethz.jingyli.mobilegis.compassnavigate.Model;

import android.util.Log;

import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

public class AppAnchor {
    private String anchorName;
    private String anchorID;
    private int count;
    // Flag if it's successfully uploaded to cloud
    private boolean uploaded = false;

    // anchor Node in ar frame
    private AnchorNode anchorNode;

    // 3D Models
    private String modelPath;
    private float renderScale;
    private Renderable model;
    private ViewRenderable titleModel;


    public AppAnchor(String anchorName){
        this.anchorName = anchorName;
    }
    public AppAnchor(String anchorName, String anchorID, int count){
        this.anchorName = anchorName;
        this.anchorID = anchorID;
        this.count = count;
    }

    public boolean drawAnchorModelinScene(ArFragment arFragment){
        if(this.model==null || this.titleModel==null || this.anchorNode==null){
            return false;
        }
        // Create the transformable model and add it to the anchor.
        TransformableNode transformableNodemodel = new TransformableNode(arFragment.getTransformationSystem());
        transformableNodemodel.getScaleController().setMaxScale(this.renderScale);
        transformableNodemodel.getScaleController().setMinScale((float) (this.renderScale*0.9));

        transformableNodemodel.setParent(this.anchorNode);
        transformableNodemodel.setRenderable(this.model);
        transformableNodemodel.getRenderableInstance().animate(true).start();
        transformableNodemodel.select();
        // Title of the model
        Node tigerTitleNode = new Node();
        tigerTitleNode.setParent(transformableNodemodel);
        tigerTitleNode.setEnabled(false);
        tigerTitleNode.setLocalPosition(new Vector3(0.0f, 1.0f, 0.0f));
        tigerTitleNode.setRenderable(this.titleModel);
        tigerTitleNode.setEnabled(true);

        return true;
    }


    public String getAnchorName() {
        return anchorName;
    }

    public void setAnchorName(String anchorName) {
        this.anchorName = anchorName;
    }

    public String getAnchorID() {
        return anchorID;
    }

    public void setAnchorID(String anchorID) {
        this.anchorID = anchorID;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public AnchorNode getAnchorNode() {
        return anchorNode;
    }

    public void setAnchorNode(AnchorNode anchorNode) {
        this.anchorNode = anchorNode;
    }

    public String getModelPath() {
        return modelPath;
    }

    public void setModelPath(String modelPath) {
        this.modelPath = modelPath;
    }

    public Renderable getModel() {
        return model;
    }

    public void setModel(Renderable model) {
        this.model = model;
    }

    public ViewRenderable getTitleModel() {
        return titleModel;
    }

    public void setTitleModel(ViewRenderable titleModel) {
        this.titleModel = titleModel;
    }

    public float getRenderScale() {
        return renderScale;
    }

    public void setRenderScale(float renderScale) {
        this.renderScale = renderScale;
    }
}
