# EbiFly

EbiFlyはBukkit用の経済機能付きFlyプラグインです。

バニラサバイバルでは出来ない飛行を可能にしながら、経済的に上限を設ける事でサバイバルの実質クリエイティブ化を防止します。

## このプラグインのここが凄い

- 飛行にお金を消費する機能 (経済無効設定も可能)
- パーティクルとサウンドを使用した視聴覚効果
- アクションバー/タイトルにメッセージを表示可能
- 飛行を無効にした時に落下/奈落/溶岩でのダメージを防ぐための安全機能
- 浮遊デバフ中や水中での飛行をさせないための制限機能
- 多言語対応

## インストール

経済機能を使用する場合は[Vault](https://www.spigotmc.org/resources/vault.34315/)と経済プラグインをインストールしてください。  
使いやすい経済プラグインをお探しの方は[Jecon](https://github.com/HimaJyun/Jecon)を使ってみてください、このプラグインの開発者が開発しています。

(経済機能を使用しない場合はVaultや経済プラグインは必要ありません。この場合でも、例えば権限を持つプレイヤーが与えた分だけ飛行できるようにするためのタイマー付き飛行プラグインとして有用です)

1. GitHubの[リリースページ](https://github.com/HimaJyun/EbiFly/releases/latest)からプラグインをダウンロードしてください。
2. ダウンロードしたjarを`plugins`ディレクトリに入れて再起動してください。
3. 必要であれば設定を調整して`/fly reload`してください。

# コマンドと権限

## コマンド

|コマンド|説明|権限|デフォルト|
|:-------|:---|:---|:---------|
|/fly|(飛行中) 飛行を無効にします。<br>(歩行中) 無限飛行を有効にします。|ebifly.fly.self|全員|
|/fly [時間] [プレイヤー]|指定した時間飛行します。(分単位)<br>(プレイヤーを指定した場合) プレイヤーを飛行させます。|ebifly.fly.self<br>ebifly.fly.other|全員|
|/fly version|プラグインのバージョンを確認します。|ebifly.version|OP|
|/fly reload|プラグインをリロードします。|ebifly.reload|OP|
|/fly help|ヘルプを表示します。|N/A|全員|

## 制限機能の権限

権限を与える(`true`にする)と有効、剥奪する(`false`にする)と無効です。

|権限|説明|デフォルト|
|:---|:---|:---------|
|ebifly.restrict.respawn|(有効) リスポーン時に飛行を継続します。<br>(無効) リスポーン時に飛行を停止します。|有効|
|ebifly.restrict.world|(有効) 世界を移動した時に飛行を継続します。<br>(無効) 世界を移動した時に飛行を停止します。|有効|
|ebifly.restrict.gamemode|(有効) ゲームモードを切り替えた時に飛行を継続します。<br>(無効) ゲームモードを切り替えた時に飛行を停止します。|無効|
|ebifly.restrict.levitation|(有効) 浮遊のデバフを受けた時に飛行を継続します。<br>(無効) 浮遊のデバフを受けた時に飛行を停止します。|OPのみ有効|
|ebifly.restrict.water|(有効) 水中に入った時に飛行を継続します。<br>(無効) 水中に入った時に飛行を無効にします。|OPのみ有効|

この機能は`config.yml`の`restrict`でも設定できます。

<!-- 説明しなくても試せば分かる。
`ebifly.restrict.gamemode`は少し直感的ではない挙動をします。  
無効であればサバイバルからクリエイティブに切り替えた時に飛行が停止しますが、有効であればそのまま継続されます。(クリエイティブモードはそのままでも飛べるため、ここでいう「継続される」とは支払いや飛行時間のカウントが停止しないという意味です。)  
これは、クリエイティブからサバイバルに戻した時にどうするべきかの設定を想定しているためです。-->

## その他の権限

|権限|説明|デフォルト|
|:---|:---|:---------|
|ebifly.free|飛行にお金を消費しなくなります|OP|
|ebifly.fly.*|次の権限を一括設定:<br>ebifly.fly.self<br>ebifly.fly.other|N/A|
|ebifly.restrict.*|次の権限を一括設定:<br>ebifly.restrict.respawn<br>ebifly.restrict.world<br>ebifly.restrict.gamemode<br>ebifly.restrict.levitation<br>ebifly.restrict.water|N/A|
|ebifly.op|次の権限を一括設定:<br>ebifly.free<br>ebifly.version<br>ebifly.reload<br>ebifly.restrict.water<br>ebifly.restrict.levitation|N/A|
|ebifly.user|次の権限を一括設定:<br>ebifly.fly.self<br>ebifly.fly.other<br>ebifly.restrict.respawn<br>ebifly.restrict.world<br>|N/A|
|ebifly.*|全ての権限を一括設定|N/A|

# 設定

設定は`config.yml`と`locale/(言語).yml`に分かれています。

主に`config.yml`が動作に関する設定で、`(言語).yml`が出力されるメッセージの設定です。

<!-- この書き方だと設定ファイルを変えるたびに更新しないといけなくてしんどいかも、勘所だけ抑えた方が良いか？ -->

## バージョン確認 (versionCheck)

`versionCheck`を`true`にすると起動時に新しいバージョンがないか確認します。

確認にはGitHubのAPIが使用されるため安全です。このプラグインは統計データも含めて外部へのデータ送信は一切行いません。

## 安全機能 (safety)

飛行時間切れ後の落下ダメージ(`fall`)、奈落への墜落(`void`)、溶岩ダイブ(`lava`)を無効にする機能です。

それぞれ、設定を`false`にする(`lava`は`0`にする)と無効になります。

```yml
safety:
  fall: true
  void: true
  lava: 30
  levitation: false
  limit: 0s
  cleanup: 1d12h
  save: 12h
```

`lava`の単位は秒です。

`levitation`を`true`にすると浮遊デバフで飛行が無効にされた際にも動作するようになります。`restrict.levitation`が`true`の時だけ効果があります。

`limit`、`cleanup`、`save`は安全機能に関する時間の設定です。これらは日(`d`)、時(`h`)、分(`m`)、秒(`s`)などの任意の単位を指定できますが、単位指定が必須です。(`30`は無効です、`30s`でなければなりません。)

`limit`は飛行を無効にしてから安全機能が無効になるまでの時間です。時間内でも安全機能が一度作動すれば無効になります。(`0`にすれば安全機能が作動するまで有効になり続けます)

`cleanup`は安全機能が有効な状態でログアウトした際にいつまでその情報を保持するかの設定です。(`0`にすれば安全機能が作動するまで保持し続けます)

`save`は安全機能が有効になっているプレイヤーの情報を定期的に保存する設定です。(`0`にすればサーバーのシャットダウン時にのみ保存されるようになります)

安全機能の状態は`safety.yml`に保存されます。ログアウト後に保持する機能が必要なければ`cleanup`を`1s`などの極端に短い時間に設定すると同様の効果があるでしょう。

## 制限機能 (restrict)

ゲームバランスを維持するために特定の状況での飛行を無効にする機能です。ユーザーごとに権限で個別に設定できます、主な効果は権限を参照してください。

```yml
restrict:
  respawn: true
  world: true
  gamemode: true
  levitation: temporary
  water: false
```

`config.yml`で`false`にする=無効ではなく、単に外的要因による飛行状態の変化を処理しなくなるだけです。

そのため、`false`にした際にどのような挙動を示すかは設定項目によって異なり、権限と同じ動作になるわけではありません。

(これは極限までパフォーマンスをチューニングしたいサーバー管理者のための設定です)

`levitation`と`water`は特殊な値`temporary`を使用でき、これはその状況が有効な時だけ(浮遊デバフがある時だけ、水中に入っている時だけ)飛行を無効にする設定です。

`water`は仕様の関係からイベント(処理)の呼び出し回数が多いです。基本的にこれが問題になることはありませんが、負荷を気にしているサーバーでは`false`にすると良いでしょう。

## 経済機能 (economy)

経済を有効にする場合は`economy.enable`を`true`にしてください。  
(Vaultと経済プラグインがインストールされていないまま`true`にするとエラーで起動しません)

```yml
economy:
  enable: false
  price: 500
  server: ""
  refund: true
```

`price`は1分あたりの料金です。

`server`を設定するとプレイヤーの支払いや返金が指定したアカウントから出し入れされるようになります。

`refund`は返金の設定です。`true`ならプレイヤーに返金、`false`なら返金しない、`payer`なら支払った人に返金されます。

## 通知 (notice)

飛行を有効にしたとき(`enable`)、無効にしたとき(`disable`)、時間切れが近い時(`timeout`)、支払いをした時(`payment`)にパーティクルを表示したり音を鳴らすための設定です。

```yml
notice:
  enable:
    particle:
      type: VILLAGER_HAPPY
      count: 10
      offset: { x: 0.5, y: 0.5, z: 0.5 }
      extra: 0
      global: true
    sound:
      type: BLOCK_BEACON_ACTIVATE
      volume: 1.0
      pitch: 1.8
      global: false
```

パーティクル(`particle`)と音(`sound`)の`type`では種類を指定します。`false`にすると無効になります。(設定可能な値: [パーティクル](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Particle.html)、[音](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Sound.html))

`global`を`true`にすると他のプレイヤーにもパーティクルや音が見える/聴こえるようになります。

`particle.offset`は3つの記法ができます。

```yml
particle:
  offset: { x: 0.5, y: 0.5, z: 0.5 }
  offset:
    x: 0.5
    y: 0.5
    z: 0.5
  offset: 0.5
```

これらはどれも同じ意味です。一番上の記法では変数名と値の間に空白が必要なので気を付けてください。(`x:0.5`は間違っています、`x: 0.5`でなければなりません)

`particle.extra`は`speed`と呼ばれる事もある値です。変更するとパーティクルの色が変わったり、速度や動き方が変わったりします。

いくつかの項目には特殊な設定項目があります。

```yml
timeout:
  position: subtitle
  second: 30
  title:
    fadeIn: 0.5
    stay: 3.5
    fadeOut: 1
payment:
  position: actionbar
```

`position`はメッセージ表示の位置を変更できます。自動で表示されるメッセージがチャットログを流してしまう事を防止します。

設定可能な値は無効(`false`)、チャット(`chat`)、タイトル(`title`)、サブタイトル(`subtitle`)、アクションバー(`actionbar`)です。

`title`と`subtitle`を使用する場合は`title`でその表示時間を設定できます。単位は秒です。

`timeout.second`は時間切れ警告の設定で、残り時間が設定した時間になると警告が表示されます。単位は秒で、60以下でなければなりません。

## 多言語対応 (locale)

このプラグインはプレイヤーの言語設定を使用してメッセージを切り替えます。(例: ゲーム側の設定で英語を使用していると表示されるメッセージは英語になります)

この機能は`enable`を`false`にすると無効にできます。その際の言語は`default`が使用されます。

```yml
locale:
  enable: true
  default: "en_us"
```

対応言語を追加したりメッセージの内容を変更したい場合は、`locale/(言語).yml`を編集してください。プラグインには`en_us.yml`と`ja_jp.yml`が含まれています。

対応言語を追加する際にはゲーム側の言語を切り替えて`/fly version`コマンドを実行してください。`Locale`行に次のような言語情報が表示されます。

```yml
Locale: 使用している言語 (プレイヤーが設定している言語)
```

`locale`ディレクトリの中に`プレイヤーが設定している言語.yml`を作成する(例: 韓国語なら`ko_kr.yml`)とその言語では作成したファイルが使用されるようになります。

翻訳元には`ja_jp.yml`などの既存のファイルをコピーして使用すると良いでしょう。
