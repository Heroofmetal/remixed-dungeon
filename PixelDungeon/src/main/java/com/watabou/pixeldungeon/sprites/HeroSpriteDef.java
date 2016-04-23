package com.watabou.pixeldungeon.sprites;

import com.watabou.gltextures.TextureCache;
import com.watabou.noosa.Animation;
import com.watabou.noosa.Camera;
import com.watabou.noosa.CompositeImage;
import com.watabou.noosa.Image;
import com.watabou.noosa.TextureFilm;
import com.watabou.noosa.tweeners.Tweener;
import com.watabou.pixeldungeon.Dungeon;
import com.watabou.pixeldungeon.actors.hero.Hero;
import com.watabou.pixeldungeon.actors.hero.HeroSubClass;
import com.watabou.pixeldungeon.items.armor.Armor;
import com.watabou.pixeldungeon.scenes.GameScene;
import com.watabou.pixeldungeon.utils.Utils;
import com.watabou.utils.Callback;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by mike on 16.04.2016.
 */
public class HeroSpriteDef extends MobSpriteDef {

	private static final int RUN_FRAMERATE = 20;

	// body goes as main texture
	private static final String LAYER_ARMOR  = "armor";
	private static final String LAYER_HEAD   = "head";
	private static final String LAYER_DEATH  = "death";
	private static final String LAYER_BODY   = "body";
	private static final String LAYER_SHIELD = "shield";
	private static final String LAYER_WEAPON = "weapon";

	private Animation fly;

	private Tweener  jumpTweener;
	private Callback jumpCallback;

	public HeroSpriteDef(Hero hero, boolean link) {
		super("spritesDesc/Hero.json",0);

		String bodyDescriptor = bodyDescriptor(hero);
		addLayer(LAYER_BODY, TextureCache.get("hero/body/"+bodyDescriptor+".png"));

		String classDescriptor = hero.heroClass.toString()+"_"+hero.subClass.toString();
		addLayer(LAYER_HEAD,  TextureCache.get("hero/head/"+classDescriptor+".png"));

		String armorDescriptor = hero.belongings.armor == null ? "no_armor" :  hero.belongings.armor.getClass().getSimpleName();
		addLayer(LAYER_ARMOR, TextureCache.get("hero/armor/"+armorDescriptor+".png"));

		String deathDescriptor = classDescriptor.equals("MAGE_WARLOCK") ? "warlock" : "common";
		addLayer(LAYER_DEATH, TextureCache.get("hero/death/"+deathDescriptor+".png"));


		if(link) {
			link(hero);
		}
	}

	private String bodyDescriptor(Hero hero) {
		if(hero.getGender()== Utils.FEMININE) {
			return "woman";
		}

		if(hero.subClass.equals(HeroSubClass.WARLOCK)) {
			return "warlock";
		}

		return "man";
	}

	@Override
	protected void loadAdditionalData(JSONObject json, TextureFilm film, int kind) throws JSONException {
		fly     = readAnimation(json, "fly", film);
		operate = readAnimation(json, "operate", film);
	}


	@Override
	public void place(int p) {
		super.place(p);
		Camera.main.target = this;
	}

	@Override
	public void move(int from, int to) {
		super.move(from, to);
		if (ch.flying) {
			play(fly);
		}
		Camera.main.target = this;
	}

	public void updateArmor(Armor armor) {
		if(armor!=null) {
			setLayerState(LAYER_ARMOR,true);
			setLayerTexture(LAYER_ARMOR, TextureCache.get("hero/armor/" + armor.getClass().getSimpleName() + ".png"));
		} else {
			setLayerState(LAYER_ARMOR,false);
		}
	}

	public void jump(int from, int to, Callback callback) {
		jumpCallback = callback;

		int distance = Dungeon.level.distance(from, to);
		jumpTweener = new JumpTweener(this, worldToCamera(to), distance * 4,
				distance * 0.1f);
		jumpTweener.listener = this;
		getParent().add(jumpTweener);

		turnTo(from, to);
		play(fly);
	}

	@Override
	public void onComplete(Tweener tweener) {
		if (tweener == jumpTweener) {

			if (getVisible() && Dungeon.level.water[ch.getPos()] && !ch.flying) {
				GameScene.ripple(ch.getPos());
			}
			if (jumpCallback != null) {
				jumpCallback.call();
			}

		} else {
			super.onComplete(tweener);
		}
	}

	public boolean sprint(boolean on) {
		run.delay = on ? 0.625f / RUN_FRAMERATE : 1f / RUN_FRAMERATE;
		return on;
	}

	public Image avatar(Hero hero) {
		CompositeImage avatar = new CompositeImage(texture);
		avatar.frame(idle.frames[0]);
		return avatar;
	}

}
