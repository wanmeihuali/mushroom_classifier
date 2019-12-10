import wikipedia
import tensorflow as tf
import json
import sqlite3

def get_na_fungi_filenames(annotations, fungi_set, is_train=True, image_ids=set()):
  if (is_train):
    with open(annotations) as JSON:
      data = json.load(JSON)
      category_id_set = set()
      for mushroom in data['categories']:
          if mushroom['name'].split('var.')[0] in fungi_set:
              category_id_set.add(mushroom['id'])
      image_id_set = set()
      for annotation in data['annotations']:
          if annotation['category_id'] in category_id_set:
              image_id_set.add(annotation['image_id'])
      na_fungi = set()
      for image in data['images']:
          if image['id'] in image_id_set:
              na_fungi.add(image['file_name'])
      return category_id_set, na_fungi
  else:
      with open(annotations) as JSON:
        data = json.load(JSON)
        na_fungi = set()
        for image in data['images']:
          if image['id'] in image_ids:
            na_fungi.add(image['file_name'])
        return None, na_fungi

def convertToBinaryData(filename):
    #Convert digital data to binary format
    with open(filename, 'rb') as file:
        blobData = file.read()
    return blobData

def insertBLOB(id, name, info, img_path):
    try:
        sqliteConnection = sqlite3.connect('fungi.db')
        cursor = sqliteConnection.cursor()
        print("Connected to SQLite")
        sqlite_insert_blob_query = """ INSERT INTO 'fungi'
                                  ('id', 'name', 'info', 'example_img') VALUES (?, ?, ?, ?)"""
        img = convertToBinaryData(img_path)
        data_tuple = (id, name, info, img)
        cursor.execute(sqlite_insert_blob_query, data_tuple)
        sqliteConnection.commit()
        print("Image and file inserted successfully as a BLOB into a table")
        cursor.close()

    except sqlite3.Error as error:
        print("Failed to insert blob data into sqlite table", error)
    finally:
        if (sqliteConnection):
            sqliteConnection.close()
            print("the sqlite connection is closed")

# Select NA fungi and place their names in train_set, val_set, and test_set
with open(r'fungi_us.txt') as file:
    fungi_data = file.readlines()
fungi_set = set()
for fungi in fungi_data:
    fungi_set.add(fungi.strip())

with open("mushrooms.txt", "r") as f:
    mushrooms = f.read()
    mushrooms = mushrooms.split("\n")

mushrooms_summary = {}
for mushroom in mushrooms:
    try:
        mushrooms_summary[mushroom] = wikipedia.summary(mushroom)
    except:
        print("wiki summary fail for: " + mushroom)

mushrooms_db = {mushroom.lower(): {"introduction": mushrooms_summary[mushroom]} for mushroom in mushrooms_summary}

tf.keras.utils.get_file(
  'training','https://data.deic.dk/public.php?service=files&t=2fd47962a38e2a70570f3be027cea57f&download',
   untar=True)
tf.keras.utils.get_file(
  'test','https://data.deic.dk/public.php?service=files&t=53f154ca9e9f1e6aee8587f5d18f81fd&download', untar=True)

train_annotation_url = 'https://data.deic.dk/public.php?service=files&t=8dc110f312677d2b53003de983b3a26e&download'
test_annotation_url = 'https://data.deic.dk/public.php?service=files&t=c899715d20e2e80063ced63d9cfec9c3&download'

tf.keras.utils.get_file('train.json', train_annotation_url, untar=True)
tf.keras.utils.get_file('annotation', train_annotation_url, untar=True)
tf.keras.utils.get_file('test.json', test_annotation_url, untar=True)


id_set, train_set = get_na_fungi_filenames(r'~/.keras/datasets/train.json', fungi_set)
_, val_set = get_na_fungi_filenames(r'~/.keras/datasets/val.json', fungi_set)
_, test_set = get_na_fungi_filenames(r'~/.keras/datasets/test.json', fungi_set, False, id_set)

print(len(train_set))
print(len(val_set))
print(len(test_set))

# analysis train.json and get one example image for each category
with open(r'~/.keras/datasets/train.json') as JSON:
    data = json.load(JSON)

example_set = {}
for annotation in data['annotations']:
    if annotation['category_id'] in id_set:
        example_set[annotation['category_id']] = annotation['image_id']

cat_to_name = {}
for cat in data['categories']:
    cat_to_name[cat["id"]] = cat["name"] 

img_id_to_path = {}
for img in data['images']:
    img_id_to_path[img["id"]] = img["file_name"]

name_to_cat = {cat_to_name[cat].lower(): cat for cat in cat_to_name}

for name in mushrooms_db:
    mushrooms_db[name]["cat_id"] = name_to_cat[name]
    mushrooms_db[name]["example"] = img_id_to_path[example_set[name_to_cat[name]]]


# create table    
con = sqlite3.connect("fungi.db")
curser = con.cursor()
query = """create table fungi (id integer, name text, info text, example_img blob)"""
curser.execute(query)
con.commit()
curser.close()

# insert information for each category
for name in mushrooms_db:
    insertBLOB(mushrooms_db[name]["cat_id"], name, mushrooms_db[name]["introduction"], r'~/.keras/datasets/' + mushrooms_db[name]["example"])